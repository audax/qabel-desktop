package de.qabel.desktop.ui.connector;

import de.qabel.core.config.Contact;
import de.qabel.core.config.Identity;
import de.qabel.core.drop.DropMessage;
import de.qabel.core.drop.DropURL;
import de.qabel.core.exceptions.*;
import de.qabel.desktop.ui.AbstractControllerTest;
import de.qabel.desktop.ui.actionlog.ActionlogController;
import de.qabel.desktop.ui.actionlog.ActionlogView;
import org.junit.Before;
import org.junit.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class ActionlogControllerTest extends AbstractControllerTest {

	ActionlogController controller;
	Identity i;
	ActionlogView view;
	Contact c;
	String fakeURL;
	String workingURL;
	String text = "MessageString";
	HttpDropConnector connector;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		fakeURL = "http://localhost:12345/abcdefghijklmnopqrstuvwxyzabcdefgworkingUrl";
		workingURL = "https://qdrop.prae.me/abcdefghijklmnopqrstuvwxyzabcdefgworkingUrl";
		connector = new HttpDropConnector();
	}


	@Test(expected = QblNetworkInvalidResponseException.class)
	public void sendDropMessageFailTest() throws QblDropPayloadSizeException, URISyntaxException, QblDropInvalidURL, QblNetworkInvalidResponseException {
		Collection<DropURL> collection = new ArrayList<>();
		DropURL drpoUrl = new DropURL(fakeURL);
		collection.add(drpoUrl);
		i = identityBuilderFactory.factory().withAlias("TestAlias").build();
		c = new Contact(i, i.getAlias(), collection, i.getEcPublicKey());
		DropMessage dropMessage = new DropMessage(i, text, "dropMessage");
		connector.send(c, dropMessage);
	}

	@Test
	public void sendAndReceiveMessagesTest() throws QblDropPayloadSizeException, QblDropInvalidMessageSizeException, QblVersionMismatchException, QblSpoofedSenderException, URISyntaxException, QblDropInvalidURL, QblNetworkInvalidResponseException {
		String text = "MessageString";
		Date sinceDate = new Date(0L);
		Collection<DropURL> collection = new ArrayList<>();
		DropURL dropURL = new DropURL(workingURL);
		collection.add(dropURL);

		i = identityBuilderFactory.factory().withAlias("TestAlias").build();
		Identity identity = new Identity("TestAlias", collection, i.getPrimaryKeyPair());
		c = new Contact(identity, identity.getAlias(), collection, identity.getEcPublicKey());

		DropMessage dropMessage = new DropMessage(identity, text, "dropMessage");

		connector.send(c, dropMessage);

		List<DropMessage> messages = connector.getDropMessages(i, sinceDate);

		assertEquals(1, messages.size());
		assertEquals(text, messages.get(0).getDropPayload());
	}


}