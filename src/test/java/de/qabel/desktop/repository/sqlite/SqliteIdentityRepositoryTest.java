package de.qabel.desktop.repository.sqlite;

import de.qabel.core.config.Identities;
import de.qabel.core.config.Identity;
import de.qabel.desktop.config.factory.DefaultIdentityFactory;
import de.qabel.desktop.config.factory.DropUrlGenerator;
import de.qabel.desktop.config.factory.IdentityBuilder;
import de.qabel.desktop.repository.EntityManager;
import de.qabel.desktop.repository.exception.EntityNotFoundExcepion;
import de.qabel.desktop.repository.sqlite.hydrator.IdentityHydrator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;

import static org.junit.Assert.*;

public class SqliteIdentityRepositoryTest {
    private Connection connection;
    private ClientDatabase clientDatabase;
    private Path dbFile;
    private SqliteIdentityRepository repo;
    private IdentityBuilder identityBuilder;
    private EntityManager em;

    @Before
    public void setUp() throws Exception {
        dbFile = Files.createTempFile("qabel", "tmpdb");
        connection = DriverManager.getConnection("jdbc:sqlite://" + dbFile.toAbsolutePath());
        clientDatabase = new DefaultClientDatabase(connection);
        clientDatabase.migrate();
        em = new EntityManager();
        IdentityHydrator hydrator = new IdentityHydrator(new DefaultIdentityFactory(), em);
        repo = new SqliteIdentityRepository(clientDatabase, hydrator);
        identityBuilder = new IdentityBuilder(new DropUrlGenerator("http://localhost"));
        identityBuilder.withAlias("testuser");
    }

    @After
    public void tearDown() throws Exception {
        try {
            connection.close();
        } finally {
            Files.delete(dbFile);
        }
    }

    @Test
    public void returnsEmptyListWithoutInstances() throws Exception {
        Identities results = repo.findAll();
        assertEquals(0, results.getIdentities().size());
    }

    @Test(expected = EntityNotFoundExcepion.class)
    public void throwsExceptionOnMissingFind() throws Exception {
        repo.find("123");
    }

    @Test
    public void findsSavedIdentities() throws Exception {
        Identity identity = identityBuilder.build();
        identity.setEmail("email");
        identity.setPhone("phone");
        repo.save(identity);
        Identity loaded = repo.find(identity.getKeyIdentifier());

        assertSame(identity, loaded);
    }

    @Test
    public void findsSavedIdentitiesFromPreviousSession() throws Exception {
        Identity identity = identityBuilder.build();
        identity.setEmail("email");
        identity.setPhone("phone");
        repo.save(identity);
        em.clear();

        Identity loaded = repo.find(identity.getKeyIdentifier());

        assertNotNull(loaded);
        assertEquals(identity.getKeyIdentifier(), loaded.getKeyIdentifier());
        assertTrue(Arrays.equals(
            identity.getPrimaryKeyPair().getPrivateKey(),
            loaded.getPrimaryKeyPair().getPrivateKey()
        ));
        assertEquals(identity.getAlias(), loaded.getAlias());
        assertEquals(identity.getEmail(), loaded.getEmail());
        assertEquals(identity.getPhone(), loaded.getPhone());
    }

    @Test
    public void findsSavedIdentitiesCollection() throws Exception {
        Identity identity = identityBuilder.build();
        identity.setEmail("email");
        identity.setPhone("phone");
        repo.save(identity);
        Identities loaded = repo.findAll();

        assertNotNull(loaded);
        assertEquals(1, loaded.getIdentities().size());
        assertSame(identity, loaded.getIdentities().toArray()[0]);
    }

    @Test
    public void findsSavedIdentitiesCollectionFromPreviousSession() throws Exception {
        Identity identity = identityBuilder.build();
        identity.setEmail("email");
        identity.setPhone("phone");
        repo.save(identity);
        em.clear();

        Identities loaded = repo.findAll();

        assertNotNull(loaded);
        assertEquals(1, loaded.getIdentities().size());
        Identity loadedIdentity = loaded.getByKeyIdentifier(identity.getKeyIdentifier());

        assertNotNull(loadedIdentity);
        assertEquals(identity.getKeyIdentifier(), loadedIdentity.getKeyIdentifier());
        assertTrue(Arrays.equals(
            identity.getPrimaryKeyPair().getPrivateKey(),
            loadedIdentity.getPrimaryKeyPair().getPrivateKey()
        ));
        assertEquals(identity.getAlias(), loadedIdentity.getAlias());
        assertEquals(identity.getEmail(), loadedIdentity.getEmail());
        assertEquals(identity.getPhone(), loadedIdentity.getPhone());
    }
}
