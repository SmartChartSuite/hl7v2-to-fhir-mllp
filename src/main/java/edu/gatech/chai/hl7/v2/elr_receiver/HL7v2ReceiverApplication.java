package edu.gatech.chai.hl7.v2.elr_receiver;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

import org.hl7.fhir.r4.model.Bundle;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// uncomment below
//import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.tape2.QueueFile;

import ca.uhn.hl7v2.hoh.api.IAuthorizationServerCallback;
import edu.gatech.chai.hl7.v2.parser.fhir.BaseHL7v2FHIRParser;

/*
 * HL7v2 Message Receiver Application for ELR
 * 
 * Author : Myung Choi (myung.choi@gtri.gatech.edu)
 * Version: 0.1-beta
 * 
 * Implementation Guide: V251_IG_LB_LABRPTPH_R2_DSTU_R1.1_2014MAY.PDF (Available from HL7.org)
 */

public abstract class HL7v2ReceiverApplication<v extends BaseHL7v2FHIRParser>
		implements IHL7v2ReceiverApplication, IAuthorizationServerCallback {
	private String controller_api_url;
	private boolean useTls;
	private QueueFile queueFile = null;
	private TimerTask timerTask = null;
	private Timer timer = null;
	private v myParser = null;
	private String hl7HttpBasic = null;
	private String saveToFile = null;
	private String authBasic = null;
	private String authBearer = null;
	private String filePath =  null;

	// Logger setup
	final static Logger LOGGER = LoggerFactory.getLogger(HL7v2ReceiverApplication.class.getName());

	// Error Status
	static int PID_ERROR = -1;

	static enum ErrorCode {
		NOERROR, MSH, PID, ORDER_OBSERVATION, LAB_RESULTS, INTERNAL;
	}

//	public static JSONObject parseJSONFile(String filename) throws JSONException, IOException {
//        String content = new String(Files.readAllBytes(Paths.get(filename)));
//        return new JSONObject(content);
//    }

	public void setMyParser(v myParser) {
		this.myParser = myParser;
	}

	public v getMyParser() {
		return myParser;
	}

	public QueueFile getQueueFile() {
		return queueFile;
	}

	public String getControllerApiUrl() {
		return controller_api_url;
	}
	
	public String getSaveToFile() {
		return saveToFile;
	}

	public String getAuthBasic() {
		return authBasic;
	}

	public String getAuthBearer() {
		return authBearer;
	}

	public String getFilePath() {
		return filePath;
	}

	public void config(
		String controller_api_url, boolean useTls, String qFileName, String saveToFile, String hl7HttpBasic,
		String authBasic, String authBearer, String filePath) throws Exception {

		this.controller_api_url = controller_api_url;
		this.useTls = useTls;
		this.saveToFile = saveToFile;
		this.hl7HttpBasic = hl7HttpBasic;
		this.authBasic = authBasic;
		this.authBearer = authBearer;
		this.filePath = filePath;

		// Set up QueueFile
		if (queueFile == null) {
			File file = new File(qFileName);
			queueFile = new QueueFile.Builder(file).build();
		}

		// After QueueFile is set up, we start background service.
		if (timerTask == null)
			timerTask = new QueueTaskTimer(this);

		if (timer == null)
			timer = new Timer(true);
		else
			timer.cancel();

		timer.scheduleAtFixedRate(timerTask, 20 * 1000, 10 * 1000);
	}

	public int process_q() {
		String jsonString = "";
		int ret = 0;
		if (queueFile.isEmpty())
			return ret;
		boolean success = true;
		try {
			byte[] data = queueFile.peek();
			queueFile.remove();
			jsonString = new String(data, StandardCharsets.UTF_8);
			System.out.println("JSON object from queue(" + queueFile.size() + "):" + jsonString);
			
			sendData(jsonString);
		} catch (JSONException e) {
			success = false;
			// We have ill-formed JSON. Remove it from queue.
			LOGGER.error("Failed to process JSON data in Queue: " + e.getMessage() + "\nJSON data:" + jsonString);
			e.printStackTrace();
		} catch (Exception e) {
			success = false;
			e.printStackTrace();
		}

		if (success) {
			ret = queueFile.size();
		} else {
			ret = -1;
		}

		return ret;
	}

	public boolean authorize(String theUriPath, String theUsername, String thePassword) {		
		LOGGER.info("Authenticating for " + theUriPath + ", " + theUsername + " and " + thePassword);

		if ("/elrreceiver".equals(theUriPath) || "/elrreceiver/".equals(theUriPath)) {
			if ((theUsername+":"+thePassword).equals(hl7HttpBasic)) {
				return true;
			}
		}
		
		return false;		
	}


	@Override
	public void sendData(String jsonString) {
		throw new UnsupportedOperationException("Unimplemented method 'sendData'");
	}

	@Override
	public void sendData(Bundle bundle) {
		throw new UnsupportedOperationException("Unimplemented method 'sendData'");
	}

}
