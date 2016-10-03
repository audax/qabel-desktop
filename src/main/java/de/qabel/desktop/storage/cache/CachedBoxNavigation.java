package de.qabel.desktop.storage.cache;

import de.qabel.box.storage.*;
import de.qabel.box.storage.command.*;
import de.qabel.box.storage.dto.BoxPath;
import de.qabel.box.storage.dto.DMChangeNotification;
import de.qabel.box.storage.exceptions.QblStorageException;
import de.qabel.core.crypto.QblECPublicKey;
import de.qabel.desktop.daemon.sync.event.ChangeEvent.TYPE;
import de.qabel.desktop.daemon.sync.event.RemoteChangeEvent;
import de.qabel.desktop.nio.boxfs.BoxFileSystem;
import de.qabel.desktop.storage.PathNavigation;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.schedulers.Schedulers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.util.List;
import java.util.Observable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.qabel.desktop.daemon.sync.event.ChangeEvent.TYPE.*;

@Deprecated
public class CachedBoxNavigation<T extends BoxNavigation> extends Observable implements BoxNavigation, PathNavigation {
    private static final Logger logger = LoggerFactory.getLogger(CachedBoxNavigation.class);
    protected final T nav;
    private final BoxNavigationCache<CachedBoxNavigation> cache = new BoxNavigationCache<>();
    protected Path path;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static de.qabel.desktop.nio.boxfs.BoxPath getEventPath(BoxObject object, BoxNavigation navigation) {
        BoxPath.FolderLike folder = navigation.getPath();
        BoxPath.Folder eventFolder;
        if (object instanceof BoxFile) {
            eventFolder = folder.resolveFile(object.getName());
        } else {
            eventFolder = folder.resolveFolder(object.getName());
        }
        de.qabel.desktop.nio.boxfs.BoxPath newPath = BoxFileSystem.getRoot();
        for (String subpath : eventFolder.toList()) {
            newPath = newPath.resolve(subpath);
        }
        System.out.println("event path: " + newPath);
        return newPath;
    }

    public CachedBoxNavigation(T nav, Path path) {
        this.nav = nav;
        this.path = path;

        nav.getChanges()
            .subscribeOn(Schedulers.immediate())
            .observeOn(Schedulers.immediate())
            .subscribe(it -> {
                try {
                    RemoteChangeEvent event = createRemoteChangeEventFromNotification(this, it);
                    setChanged();
                    notifyObservers(event);
                } catch (Exception e) {
                    logger.error("failed to propagate event: " + it.getChange() + " , " + e.getMessage(), e);
                }
            });
        throw new IllegalStateException("asd");
    }

    @NotNull
    public static RemoteChangeEvent createRemoteChangeEventFromNotification(ReadableBoxNavigation nav, DMChangeNotification it) {
        DMChange change = it.getChange();
        BoxNavigation eventNav = it.getNavigation();
        RemoteChangeEvent event = null;
        if (change instanceof UpdateFileChange) {
            UpdateFileChange update = (UpdateFileChange) change;
            BoxFile newFile = update.getNewFile();
            if (update.getExpectedFile() == null) {
                event = createRemoteChangeEvent(nav, newFile, CREATE, getEventPath(newFile, eventNav));
            } else {
                event = createRemoteChangeEvent(nav, newFile, UPDATE, getEventPath(newFile, eventNav));
            }
        } else if (change instanceof DeleteFileChange) {
            BoxFile deletedFile = ((DeleteFileChange) change).getFile();
            event = createRemoteChangeEvent(nav, deletedFile, DELETE, getEventPath(deletedFile, eventNav));
        } else if (change instanceof CreateFolderChange) {
            BoxFolder changedFolder = ((CreateFolderChange) change).getFolder();
            event = createRemoteChangeEvent(nav, changedFolder, CREATE, getEventPath(changedFolder, eventNav));
        } else if (change instanceof DeleteFolderChange) {
            BoxFolder deletedFolder = ((DeleteFolderChange) change).getFolder();
            event = createRemoteChangeEvent(nav, deletedFolder, DELETE, getEventPath(deletedFolder, eventNav));
        } else if (change instanceof ShareChange) {
            BoxFile sharedFile = ((ShareChange) change).getFile();
            event = createRemoteChangeEvent(nav, sharedFile, SHARE, getEventPath(sharedFile, eventNav));
        } else if (change instanceof UnshareChange) {
            BoxFile unsharedFile = ((UnshareChange)change).getFile();
            event = createRemoteChangeEvent(nav, unsharedFile, UNSHARE, getEventPath(unsharedFile, eventNav));
        }
        if (event == null) {
            throw new IllegalStateException("cannot create remote event from change " + change);
        }
        return event;
    }


    public static RemoteChangeEvent createRemoteChangeEvent(ReadableBoxNavigation navi, BoxObject file, TYPE type, Path targetPath) {
        System.out.println("Notify " + targetPath);
        Long mtime = file instanceof BoxFile ? ((BoxFile) file).getMtime() : null;
        if (type == DELETE) {
            mtime = System.currentTimeMillis();
        }
        return new RemoteChangeEvent(
            targetPath,
            file instanceof BoxFolder,
            mtime,
            type,
            file,
            navi
        );
    }

    @Override
    public DirectoryMetadata reloadMetadata() throws QblStorageException {
        return nav.reloadMetadata();
    }

    @Override
    public void setMetadata(DirectoryMetadata dm) {
        nav.setMetadata(dm);
    }

    @Override
    public void commit() throws QblStorageException {
        nav.commit();
    }

    @Override
    public void commitIfChanged() throws QblStorageException {
        nav.commitIfChanged();
    }

    public T getNav() {
        return nav;
    }

    @Override
    public synchronized CachedBoxNavigation navigate(BoxFolder target) throws QblStorageException {
        if (!cache.has(target)) {

            CachedBoxNavigation subnav = new CachedBoxNavigation(
                nav.navigate(target),
                BoxFileSystem.get(path).resolve(target.getName())
            );
            cache.cache(target, subnav);
        }
        return cache.get(target);
    }

    @Override
    public BoxNavigation navigate(BoxExternal target) {
        return nav.navigate(target);
    }

    @Override
    public List<BoxFile> listFiles() throws QblStorageException {
        return nav.listFiles();
    }

    @Override
    public List<BoxFolder> listFolders() throws QblStorageException {
        return nav.listFolders();
    }

    @Override
    public List<BoxExternal> listExternals() throws QblStorageException {
        return nav.listExternals();
    }

    @Override
    public BoxFile upload(String name, File file, ProgressListener listener) throws QblStorageException {
        return nav.upload(name, file, listener);
    }

    @Override
    public boolean isUnmodified() {
        return nav.isUnmodified();
    }

    @Override
    public BoxFile upload(String name, File file) throws QblStorageException {
        return nav.upload(name, file);
    }

    protected void notifyAsync(BoxObject boxObject, TYPE type, Path path) {
        executor.submit(() -> notify(boxObject, type, getDesktopPath(boxObject)));
    }

    protected void notifyAsync(BoxObject boxObject, TYPE type) {
        notifyAsync(boxObject, type, getDesktopPath(boxObject));
    }

    @Override
    public BoxFile overwrite(String name, File file, ProgressListener listener) throws QblStorageException {
        return nav.overwrite(name, file, listener);
    }

    @Override
    public BoxFile overwrite(String name, File file) throws QblStorageException {
        return nav.overwrite(name, file);
    }

    @Override
    public InputStream download(BoxFile file, ProgressListener listener) throws QblStorageException {
        return nav.download(file, listener);
    }

    @Override
    public InputStream download(BoxFile file) throws QblStorageException {
        return nav.download(file);
    }

    @Override
    public FileMetadata getFileMetadata(BoxFile boxFile) throws IOException, InvalidKeyException, QblStorageException {
        return nav.getFileMetadata(boxFile);
    }

    @Override
    public BoxFolder createFolder(String name) throws QblStorageException {
        return nav.createFolder(name);
    }

    @Override
    public void delete(BoxFile file) throws QblStorageException {
        nav.delete(file);
    }

    @Override
    public void unshare(BoxObject boxObject) throws QblStorageException {
        nav.unshare(boxObject);
        notifyAsync(boxObject, UNSHARE);
    }

    @Override
    public void delete(BoxFolder folder) throws QblStorageException {
        nav.delete(folder);
        cache.remove(folder);
    }

    @Override
    public void delete(BoxExternal external) throws QblStorageException {
        nav.delete(external);
    }

    @Override
    public void setAutocommit(boolean autocommit) {
        nav.setAutocommit(autocommit);
    }

    @Override
    public void setAutocommitDelay(long delay) {
        nav.setAutocommitDelay(delay);
    }

    @Override
    public CachedBoxNavigation navigate(String folderName) throws QblStorageException {
        return navigate(getFolder(folderName));
    }

    @Override
    public BoxFolder getFolder(String name) throws QblStorageException {
        return nav.getFolder(name);
    }

    @Override
    public boolean hasFolder(String name) throws QblStorageException {
        return nav.hasFolder(name);
    }

    @Override
    public BoxFile getFile(String name) throws QblStorageException {
        return nav.getFile(name);
    }

    @Override
    public DirectoryMetadata getMetadata() {
        return nav.getMetadata();
    }

    @Override
    public BoxExternalReference share(QblECPublicKey owner, BoxFile file, String receiver) throws QblStorageException {
        BoxExternalReference share = nav.share(owner, file, receiver);
        notifyAsync(file, SHARE);
        return share;
    }

    @Override
    public List<BoxShare> getSharesOf(BoxObject object) throws QblStorageException {
        return nav.getSharesOf(object);
    }

    public void refresh() throws QblStorageException {
        refresh(true);
    }

    @Override
    public boolean hasFile(String name) throws QblStorageException {
        return nav.hasFile(name);
    }

    private void notify(BoxObject file, TYPE type, Path targetPath) {
        System.out.println("Notify " + targetPath);
        setChanged();
        Long mtime = file instanceof BoxFile ? ((BoxFile) file).getMtime() : null;
        if (type == DELETE) {
            mtime = System.currentTimeMillis();
        }
        RemoteChangeEvent event = new RemoteChangeEvent(
            targetPath,
            file instanceof BoxFolder,
            mtime,
            type,
            file,
            this
        );
        notifyObservers(
            event
        );
    }

    public void notifyAllContents() throws QblStorageException {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        // TODO notify async and sync files first (better UX on files)
        for (BoxFolder folder : nav.listFolders()) {
            notify(folder, CREATE, getDesktopPath(folder));
            navigate(folder).notifyAllContents();
        }
        for (BoxFile file : listFiles()) {
            notify(file, CREATE, getDesktopPath(file));
        }
    }

    @Override
    public Path getDesktopPath() {
        return path;
    }

    @Override
    public Path getDesktopPath(BoxObject folder) {
        return BoxFileSystem.get(path).resolve(folder.getName());
    }

    @NotNull
    @Override
    public BoxFile upload(String s, InputStream inputStream, long l, ProgressListener progressListener) throws QblStorageException {
        return nav.upload(s, inputStream, l, progressListener);
    }

    @NotNull
    @Override
    public BoxFile upload(String s, InputStream inputStream, long l) throws QblStorageException {
        return nav.upload(s, inputStream, l);
    }

    @NotNull
    @Override
    public InputStream download(String s) throws QblStorageException {
        return nav.download(s);
    }

    @Override
    public boolean hasVersionChanged(DirectoryMetadata directoryMetadata) throws QblStorageException {
        return nav.hasVersionChanged(directoryMetadata);
    }

    @Override
    public void refresh(boolean recursive) throws QblStorageException {
        nav.refresh(recursive);
    }

    @NotNull
    @Override
    public rx.Observable<DMChangeNotification> getChanges() {
        return nav.getChanges();
    }

    @NotNull
    @Override
    public FileMetadata getMetadataFile(Share share) throws IOException, InvalidKeyException, QblStorageException {
        return nav.getMetadataFile(share);
    }

    @NotNull
    @Override
    public BoxPath.FolderLike getPath() {
        return nav.getPath();
    }

    @Override
    public void visit(Function2<? super AbstractNavigation, ? super BoxObject, Unit> function2) {
        nav.visit(function2);
    }

    @NotNull
    @Override
    public BoxExternalReference getExternalReference(QblECPublicKey qblECPublicKey, BoxFile boxFile) {
        return nav.getExternalReference(qblECPublicKey, boxFile);
    }
}
