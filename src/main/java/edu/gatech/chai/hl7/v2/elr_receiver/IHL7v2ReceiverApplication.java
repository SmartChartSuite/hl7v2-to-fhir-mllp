package edu.gatech.chai.hl7.v2.elr_receiver;

import org.hl7.fhir.r4.model.Bundle;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.protocol.ReceivingApplication;

public interface IHL7v2ReceiverApplication extends ReceivingApplication<Message> {
	public void sendData(String jsonString);
	public void sendData(Bundle bundle);
}
