package edu.gatech.chai.hl7.v2.elr_receiver;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.protocol.ReceivingApplicationException;
import ca.uhn.hl7v2.util.Terser;
import edu.gatech.chai.hl7.v2.parser.fhir.BaseHL7v2FHIRParser;
import edu.gatech.chai.hl7.v2.parser.fhir.HL7v23FhirR4Parser;
import edu.gatech.chai.hl7.v2.parser.fhir.HL7v251FhirR4Parser;

/*
 * HL7v2 Message Receiver Application for ELR
 * 
 * Author : Myung Choi (myung.choi@gtri.gatech.edu)
 * Version: 0.1-beta
 * 
 * Implementation Guide: V251_IG_LB_LABRPTPH_R2_DSTU_R1.1_2014MAY.PDF (Available from HL7.org)
 */

public class HL7v2ReceiverFHIRApplication<v extends BaseHL7v2FHIRParser> extends HL7v2ReceiverApplication<v> {
	private FhirContext ctx = null;
	static long expireSeconds = 0L;
	static String accessToken = null;
	static String tokenType = null;

	// Logger setup
	final static Logger LOGGER = LoggerFactory.getLogger(HL7v2ReceiverFHIRApplication.class.getName());

	public HL7v2ReceiverFHIRApplication() {
		ctx = FhirContext.forR4();
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean canProcess(Message theMessage) {
		// We accepts when the follow conditions met.
		// - MSH-21 Message Profile Identifier: We need to talk to Lab (eg Labcorp) to
		// make sure
		// that we use correct profile. ELR document has the following in the example
		// LRI_Common_Component^^2.16.840.1.113883.9
		// .16^ISO~LRI_GU_Component^^2.16.840.1.113883.9.12^ISO~LAB_RU_Componen
		// t^^2.16.840.1.113883.9.14^ISO
		// - MSH-12.1: 2.5.1 or 2.3.1 We support HL7 v2.5.1 or v2.3.1
		// - MSH-9(1.2.3): ORU^R01^ORU_R01 messages (in 1.10.1)

		// Check MSH-21 for the message profile
		// TODO: Implement this after discussing with LabCorp

		// Check the version = v2.5.1 or v2.3.1
		if (theMessage.getVersion().equalsIgnoreCase("2.3") == true) {
			LOGGER.info("Message Received with v2.3. Setting a parser for FHIR R4");
			setMyParser((v) new HL7v23FhirR4Parser());
		} if (theMessage.getVersion().equalsIgnoreCase("2.5.1") == true) {
			LOGGER.info("Message Received with v2.5.1. Setting a parser for FHIR R4");
			setMyParser((v) new HL7v251FhirR4Parser());
		} else {
			LOGGER.info("Message Received, but is not v2.3. Received message version is " + theMessage.getVersion());
			return false;
		}

		// Check the message type
		Terser t = new Terser(theMessage);
		try {
			String MSH91 = t.get("/MSH-9-1");
			String MSH92 = t.get("/MSH-9-2");
			String MSH93 = t.get("/MSH-9-3");

			if ((MSH91 != null && MSH91.equalsIgnoreCase("ORU") == false)
					|| (MSH92 != null && MSH92.equalsIgnoreCase("R01") == false)
					|| (MSH93 != null && MSH93.equalsIgnoreCase("ORU_R01") == false)) {
				LOGGER.info(
						"Message with correct version received, but not ORU_R01 message type. Receved message type: "
								+ t.get("/MSH-9-1") + " " + t.get("/MSH-9-2") + " " + t.get("/MSH-9-3"));
				return false;
			}
		} catch (HL7Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	private Bundle makeTransactionFromMessage (Bundle bundle) {
		// Write transaction
		Bundle transactionBundle = (Bundle) bundle;
		transactionBundle.setType(BundleType.TRANSACTION);
		List<BundleEntryComponent> entries = transactionBundle.getEntry();
		for (BundleEntryComponent entry : entries) {
			Resource resource = entry.getResource();
			ResourceType resourceType = resource.getResourceType();
			String resourceTypeString = resourceType.name();
			BundleEntryRequestComponent entryRequest = entry.getRequest();
			entryRequest.setMethod(HTTPVerb.POST);
			entryRequest.setUrl(resourceTypeString);
		}
		
		return transactionBundle;
	}

	public void saveJsonToFile(IBaseResource resource, String filename) {
		try {
			if (filename != null && !filename.isEmpty()) {
				String fhirJson = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);

				BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
				writer.write(fhirJson);
				writer.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

//			System.out.println(fhirJsonObject.toString());
//			also need it to save to a file
	}

	public Message processMessage(Message theMessage, Map<String, Object> theMetadata)
			throws ReceivingApplicationException, HL7Exception {

		LOGGER.debug("Received message:\n" + theMessage.encode());
		
		List<IBaseBundle> bundles = getMyParser().executeParser(theMessage);
		IGenericClient client = null;
		if (getControllerApiUrl() != null) {
			client = ctx.newRestfulGenericClient(getControllerApiUrl());
			if (getAuthBasic() != null && !getAuthBasic().isEmpty()) {
				client.registerInterceptor(new BasicAuthInterceptor(getAuthBasic()));
			} else if (getAuthBearer() != null && !getAuthBearer().isEmpty()) {
				client.registerInterceptor(new BearerTokenAuthInterceptor(getAuthBearer()));
			}
		}

		for (IBaseBundle bundle : bundles) {
			// this bundle is message bundle. Strip off the message wrapper and use the focused bundle.
			if (((Bundle) bundle).getType() != Bundle.BundleType.MESSAGE) continue;

			BundleEntryComponent messageHeaderEntry = ((Bundle) bundle).getEntryFirstRep();
			MessageHeader mh = (MessageHeader) messageHeaderEntry.getResource();
			Reference focusReference = mh.getFocusFirstRep();

			Bundle documentBundle = null;
			for (BundleEntryComponent entry : ((Bundle) bundle).getEntry()) {
				if (focusReference.getReference() != null && 
					focusReference.getReference().equals(entry.getFullUrl())) {
					if (entry.getResource() instanceof Bundle) {
						documentBundle = (Bundle) entry.getResource();
						break;
					}
				}
			}

			if (documentBundle == null) continue;
			// bundle = makeTransactionFromMessage((Bundle)bundle);

			// change it to transaction bundle.
			// bundle = makeTransactionFromMessage((Bundle)bundle);
			if (getControllerApiUrl() != null) {
				// .. process the message ..
				try {
					sendFhir(documentBundle, client);
				} catch (HL7Exception | ReceivingApplicationException | IOException e) {
					throw new ReceivingApplicationException("Sending to FHIR controller Failed", e.getCause());
				}
			}
		}

		/*
		* Now reply to the message
		*/
		Message response;
		try {
			response = theMessage.generateACK();
		} catch (IOException e) {
			throw new ReceivingApplicationException(e);
		}

		return response;
	}

	private void sendFhir(IBaseBundle bundle, IGenericClient client)
				throws ReceivingApplicationException, HL7Exception, IOException {

		// Create Parameters and add the bundle.
		// First, find a patient.
		Bundle myBundle = (Bundle) bundle;
		String MRN = null;
		String SSN = null;
		String patientIdValue = null;
		for (BundleEntryComponent entry : myBundle.getEntry()) {
			Resource resource = entry.getResource();
			if (resource instanceof Patient) {
				Patient patient = (Patient) resource;
				for (Identifier identifier : patient.getIdentifier()) {
					CodeableConcept type = identifier.getType();
					for (Coding coding : type.getCoding()) {
						if ("http://hl7.org/fhir/v2/0203".equals(coding.getSystem())) {
							if ("MR".equals(coding.getCode())) {
								MRN = identifier.getValue();
							} else if ("SS".equals(coding.getCode())) {
								SSN = identifier.getValue();
							}
						}					
					}

					patientIdValue = identifier.getValue();
				}
			}
		}

		Parameters parameters = new Parameters();
		if (MRN != null && !MRN.isBlank()) {
			parameters.setParameter("patient-identifier", MRN);
		} else if (SSN != null && !SSN.isBlank()) {
			parameters.setParameter("patient-identifier", SSN);
		} else if (patientIdValue != null && !patientIdValue.isBlank()) {
			parameters.setParameter("patient-identifier", patientIdValue);
		} else {
			LOGGER.error("Patient.identifier not found.");
			return; // silently return
		}

		// parameters.setParameter("set-status", "REQUEST");
		ParametersParameterComponent param = parameters.addParameter();
		param.setName("lab-results");
		param.setResource(myBundle);
		
		String fileUnique = String.valueOf(System.currentTimeMillis());
		if ("YES".equalsIgnoreCase(getSaveToFile())) {					
			String filename = getFilePath() + "/" + fileUnique + "_bundle.txt";
			saveJsonToFile(parameters, filename);
		} 
	
		Parameters retParams;
		try {
			retParams = client.operation()
				.onServer()
				.named("$registry-control")
				.withParameters(parameters)
				.execute();
		} catch (UnprocessableEntityException e) {
			String fhirJson = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(myBundle);
			getQueueFile().add(fhirJson.getBytes());
			
			OperationOutcome oo = (OperationOutcome) e.getOperationOutcome();
			String ooIssueText = "";
			for (OperationOutcomeIssueComponent issue : oo.getIssue()) {
				ooIssueText += issue.getDetails().getText();
			}

			ooIssueText = ooIssueText.trim();
			if (ooIssueText.isBlank()) {
				throw new ReceivingApplicationException(e);
			} else {
				throw new ReceivingApplicationException(ooIssueText);
			}
		} catch (Exception e) {
			String fhirJson = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(myBundle);
			getQueueFile().add(fhirJson.getBytes());
			
			throw new ReceivingApplicationException(e);
		}
	}

	public void sendData(String jsonString) {
		try {
			Bundle myBundle = ctx.newJsonParser().parseResource(Bundle.class, jsonString);

			IGenericClient client = null;
			if (getControllerApiUrl() != null) {
				client = ctx.newRestfulGenericClient(getControllerApiUrl());
				if (getAuthBasic() != null && !getAuthBasic().isEmpty()) {
					client.registerInterceptor(new BasicAuthInterceptor(getAuthBasic()));
				} else if (getAuthBearer() != null && !getAuthBearer().isEmpty()) {
					client.registerInterceptor(new BearerTokenAuthInterceptor(getAuthBearer()));
				}
			}
	
			sendFhir(myBundle, client);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
