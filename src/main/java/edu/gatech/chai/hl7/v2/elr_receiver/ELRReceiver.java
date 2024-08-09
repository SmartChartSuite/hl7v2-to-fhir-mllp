package edu.gatech.chai.hl7.v2.elr_receiver;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.app.ConnectionListener;
import ca.uhn.hl7v2.app.HL7Service;
import ca.uhn.hl7v2.app.SimpleServer;
import ca.uhn.hl7v2.hoh.llp.Hl7OverHttpLowerLayerProtocol;
import ca.uhn.hl7v2.hoh.util.ServerRoleEnum;
import ca.uhn.hl7v2.llp.LowerLayerProtocol;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.protocol.ReceivingApplication;
import ca.uhn.hl7v2.protocol.ReceivingApplicationExceptionHandler;

/**
 * HL7v2 Receiver This application will listen to ELR sent from laboratory. The
 * ELR should be mapped to ECR and sent to PHCR controller using /ECR Post API.
 *
 * version: 0.0.1 last updated: 7/21/2017
 * 
 * License:
 */
public class ELRReceiver {
	// Logger setup
	final static Logger LOGGER = LoggerFactory.getLogger(ELRReceiver.class.getName());

	static String default_port = "8888";
	static String default_fhir_controller_api_url = "http://localhost:8080/fhir";
	static boolean default_useTls = false;
	static String default_useTls_str = "False";
	static String default_qFileName = "queueELR";
	static String default_transport = "MLLP";
	static String default_hl7HttpBasic = "user:password";
	static String default_saveToFile = "NO";
	static String default_authBasic = "client:secret";
	static String default_authBearer = "1234";
	static String default_filePath = "./";

	@SuppressWarnings("rawtypes")
	public static void main(String[] args) throws Exception {
		Properties prop = new Properties();
		OutputStream output = null;
		InputStream input = null;
		Integer port = Integer.parseInt(default_port);
		boolean useTls = default_useTls;
		String fhir_controller_api_url = default_fhir_controller_api_url;
		String qFileName = default_qFileName;
		String transport = default_transport;
		String hl7HttpBasic = default_hl7HttpBasic;
		String saveToFile = default_saveToFile;
		String authBasic = default_authBasic;
		String authBearer = default_authBearer;
		String filePath = default_filePath;
	
		String env_saveToFile = System.getenv("SAVE_TO_FILE");
		if (env_saveToFile != null && !env_saveToFile.isBlank()) {
			default_saveToFile = env_saveToFile;
		}

		String env_authBasic = System.getenv("AUTH_BASIC");
		if (env_authBasic != null && !env_authBasic.isBlank()) {
			default_authBasic = env_authBasic;
		}

		String env_authBearer = System.getenv("AUTH_BEARER");
		if (env_authBearer != null && !env_authBearer.isBlank()) {
			default_authBearer = env_authBearer;
		}

		boolean writeConfig = false;
		try {
			input = new FileInputStream("config.properties");
			prop.load(input);

			port = Integer.parseInt(prop.getProperty("port", default_port));
			fhir_controller_api_url = prop.getProperty("fhirControllerUrl", default_fhir_controller_api_url);
			qFileName = prop.getProperty("qFileName", default_qFileName);
			transport = prop.getProperty("transport", default_transport);
			hl7HttpBasic = prop.getProperty("hl7HttpBasic", default_hl7HttpBasic);
			saveToFile = prop.getProperty("saveToFile", default_saveToFile);
			authBasic = prop.getProperty("authBasic", default_authBasic);
			authBearer = prop.getProperty("authBearer", default_authBearer);
			filePath = prop.getProperty("filePath", default_filePath);

			if (prop.getProperty("useTls", default_useTls_str).equalsIgnoreCase("true")) {
				useTls = true;
			} else {
				useTls = false;
			}
		} catch (Exception e) {
			writeConfig = true;
			e.printStackTrace();
		} finally {
			if (writeConfig) {
				output = new FileOutputStream("config.properties");
				prop.setProperty("port", default_port);
				prop.setProperty("fhirControllerUrl", default_fhir_controller_api_url);
				prop.setProperty("useTls", default_useTls_str);
				prop.setProperty("qFileName", default_qFileName);
				prop.setProperty("transport", default_transport);
				prop.setProperty("hl7HttpBasic", default_hl7HttpBasic);
				prop.setProperty("saveToFile", default_saveToFile);
				prop.setProperty("authBasic", default_hl7HttpBasic);
				prop.setProperty("authBearer", default_authBearer);
				prop.setProperty("filePath", default_filePath);
				prop.store(output, null);
			}
		}

		String envFhirUrl = System.getenv("FHIR_CONTROLLER_API_URL");
		if (envFhirUrl != null && !envFhirUrl.isEmpty()) {
			if (!envFhirUrl.startsWith("http://") && !envFhirUrl.startsWith("https://")) {
				envFhirUrl = "http://"+envFhirUrl;
			}
			
			if (envFhirUrl.endsWith("/")) {
				envFhirUrl = envFhirUrl.substring(0, envFhirUrl.length()-1);
			}
			
			fhir_controller_api_url = envFhirUrl;
		}
		
		// transport mode override by environment variable
		String envTransport = System.getenv("TRANSPORT_MODE");
		if (envTransport != null && !envTransport.isEmpty()) {
			transport = envTransport;
		}

		if ("MLLP".equals(transport)) {
			HapiContext ctx = new DefaultHapiContext();
			LOGGER.debug("Starting with MLLP");
			HL7Service server = ctx.newServer(port, useTls);

			LOGGER.debug("Preparing for FHIR parser");
			HL7v2ReceiverFHIRApplication handler = new HL7v2ReceiverFHIRApplication();
			server.registerApplication("*", "*", (ReceivingApplication<Message>) handler);

			// Configure the Receiver App before we start.
			handler.config(fhir_controller_api_url, useTls, qFileName, saveToFile, null, authBasic, authBearer, filePath);

			server.registerConnectionListener(new MyConnectionListener());
			server.setExceptionHandler(new MyExceptionHandler());

			server.startAndWait();
			LOGGER.debug("MLLP server started");
		} else {
			LOGGER.debug("Starting with HTTP");
			LowerLayerProtocol llp;
			llp = new Hl7OverHttpLowerLayerProtocol(ServerRoleEnum.SERVER);

			PipeParser parser = PipeParser.getInstanceWithNoValidation();
			SimpleServer server = new SimpleServer(port, llp, parser);
			server.setExceptionHandler(new MyExceptionHandler());

			HL7v2ReceiverFHIRApplication handler = new HL7v2ReceiverFHIRApplication();
			((Hl7OverHttpLowerLayerProtocol) llp).setAuthorizationCallback(handler);

			server.registerApplication("*", "*", (ReceivingApplication<Message>) handler);
			// Configure the Receiver App before we start.
			handler.config(fhir_controller_api_url, useTls, qFileName, saveToFile, hl7HttpBasic, authBasic, authBearer, filePath);

			server.registerConnectionListener(new MyConnectionListener());
			server.setExceptionHandler(new MyExceptionHandler());

			server.start();
			LOGGER.debug("HTTP server started");
		}

	}

	public static class MyConnectionListener implements ConnectionListener {

		public void connectionDiscarded(Connection theC) {
			LOGGER.info("Lost connection from: " + theC.getRemoteAddress().toString());
		}

		public void connectionReceived(Connection theC) {
			LOGGER.info("New connection received: " + theC.getRemoteAddress().toString());
		}

	}

	/**
	 * Process an exception.
	 * 
	 * @param theIncomingMessage  the incoming message. This is the raw message
	 *                            which was received from the external system
	 * @param theIncomingMetadata Any metadata that accompanies the incoming
	 *                            message. See
	 *                            {@link ca.uhn.hl7v2.protocol.Transportable#getMetadata()}
	 * @param theOutgoingMessage  the outgoing message. The response NAK message
	 *                            generated by HAPI.
	 * @param theE                the exception which was received
	 * @return The new outgoing message. This can be set to the value provided by
	 *         HAPI in <code>outgoingMessage</code>, or may be replaced with another
	 *         message. <b>This method may not return <code>null</code></b>.
	 */
	public static class MyExceptionHandler implements ReceivingApplicationExceptionHandler {

		public String processException(String theIncomingMessage, Map<String, Object> theIncomingMetadata,
				String theOutgoingMessage, Exception theE) {
			LOGGER.error("processException(incoming):\n" + theIncomingMessage + "\n\n");
			LOGGER.error("processException(outgoing):\n" + theOutgoingMessage + "\n\n");
			LOGGER.error("Exception:", theE);

			if (theOutgoingMessage == null || theOutgoingMessage.isEmpty()) {
				// String errorMessage = "ERR|||^^^^^^^^" + theE.getMessage() + "|E|||2.5.1";
				ZonedDateTime dateTimeNow = ZonedDateTime.now();
        		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss.SSSSZ");
       			String dateTime = dateTimeNow.format(formatter);
				String messageControlId = (String) theIncomingMetadata.get("/MSH-10");
				int myMessageControlId = 100 + (int)(Math.random() * 100000);
				String errorMessage = "MSH|^~\\&|ELR_RECEIVER|PACER-CLIENT|||" + dateTime + "||ACK^R01^ACK|" + myMessageControlId + "|P|2.5.1\r"
					+ "MSA|AE|" + messageControlId + "\r"
					+ "ERR|||^^^^^^^^" + theE.getMessage() + "|E";

				LOGGER.info("error response: " + errorMessage.replace("\r", "\n"));
				return errorMessage;
			} 

			LOGGER.info("error response: " + theOutgoingMessage.replace("\r", "\n"));
			return theOutgoingMessage;
		}

	}

}
