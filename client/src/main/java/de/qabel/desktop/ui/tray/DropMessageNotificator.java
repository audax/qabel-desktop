package de.qabel.desktop.ui.tray;

import de.qabel.core.config.Contact;
import de.qabel.core.drop.DropMessage;
import de.qabel.core.event.EventSource;
import de.qabel.desktop.daemon.drop.MessageReceivedEvent;
import de.qabel.desktop.ui.actionlog.PersistenceDropMessage;
import de.qabel.desktop.ui.actionlog.item.renderer.MessageRendererFactory;
import de.qabel.desktop.util.Translator;
import javafx.application.Platform;

import javax.inject.Inject;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

public class DropMessageNotificator {
    private MessageRendererFactory messageRendererFactory;
    private ResourceBundle resourceBundle;
    private Translator translator;
    private QabelTray tray;

    @Inject
    public DropMessageNotificator(
        MessageRendererFactory messageRendererFactory,
        ResourceBundle resourceBundle,
        Translator translator,
        QabelTray tray
    ) {
        this.messageRendererFactory = messageRendererFactory;
        this.resourceBundle = resourceBundle;
        this.translator = translator;
        this.tray = tray;
    }

    public void subscribe(EventSource events) {
        events.events(MessageReceivedEvent.class)
            .map(MessageReceivedEvent::getMessage)
            .filter(message -> !message.isSeen() && !message.isSent())
            .debounce(1, TimeUnit.SECONDS)
            .subscribe(message -> {
                String title = renderTitle(message);
                String content = renderPlaintextMessage(message);
                Platform.runLater(() -> tray.showNotification(title, content));
            });
    }

    private String renderTitle(PersistenceDropMessage message) {
        return translator.getString("newMessageNotification", ((Contact) message.getSender()).getAlias());
    }

    private String renderPlaintextMessage(PersistenceDropMessage message) {
        DropMessage dropMessage = message.getDropMessage();
        return messageRendererFactory
            .getRenderer(dropMessage.getDropPayloadType())
            .renderString(dropMessage.getDropPayload(), resourceBundle);
    }
}
