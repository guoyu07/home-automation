package co.uk.rob.apartment.automation.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import co.uk.rob.apartment.automation.model.DeviceListManager;
import co.uk.rob.apartment.automation.model.Zone;
import co.uk.rob.apartment.automation.model.devices.Blind;
import co.uk.rob.apartment.automation.model.interfaces.ControllableDevice;
import co.uk.rob.apartment.automation.model.interfaces.ReportingDevice;
import co.uk.rob.apartment.automation.utilities.CommonQueries;
import co.uk.rob.apartment.automation.utilities.HomeAutomationProperties;

/**
 * Servlet implementation class LoungeKitchenController
 */
@WebServlet("/LoungeKitchenController")
public class LoungeKitchenController extends HttpServlet {
	private Logger log = Logger.getLogger(LoungeKitchenController.class);
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public LoungeKitchenController() {
        super();
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String action = request.getParameter("action");
		boolean successfulCall = false;
		String activeUser = (String) request.getSession().getAttribute("activeUser");
		if (activeUser == null) {
			activeUser = "";
		}
		
		List<ControllableDevice> devicesInLoungeAndKitchen = DeviceListManager.getControllableDeviceByLocation(Zone.LOUNGE);
		devicesInLoungeAndKitchen.addAll(DeviceListManager.getControllableDeviceByLocation(Zone.KITCHEN));
		
		ControllableDevice lampOneLounge = devicesInLoungeAndKitchen.get(0);
		ControllableDevice ledRodLounge = devicesInLoungeAndKitchen.get(1);
		ControllableDevice lampTwoLounge = devicesInLoungeAndKitchen.get(2);
		Blind loungeWindowBlind = (Blind) devicesInLoungeAndKitchen.get(3);
		Blind loungePatioBlind = (Blind) devicesInLoungeAndKitchen.get(4);
		
		ReportingDevice patioDoor = DeviceListManager.getReportingDeviceByLocation(Zone.PATIO).get(1);
		
		PrintWriter out = response.getWriter();
		
		if (action == null || action.equals("")) {
			RequestDispatcher dispatch = request.getRequestDispatcher("index.html");
			dispatch.forward(request, response);
		}
		else if (action.equals("fullOnLounge")) {
			log.info("Request for all lights on in Lounge and Kitchen [" + activeUser + "]");
			successfulCall = lampOneLounge.turnDeviceOn(true, "99");
			lampOneLounge.resetAutoOverridden();
			if (successfulCall) {
				successfulCall = ledRodLounge.turnDeviceOff(true);
				ledRodLounge.resetAutoOverridden();
				if (successfulCall) {
					successfulCall = lampTwoLounge.turnDeviceOn(true, "99");
					lampTwoLounge.resetAutoOverridden();
				}
			}
			
			if (successfulCall) {
				out.print("Lights now on full in lounge");
			}
			else {
				out.print("Issue turning all lights on full");
			}
		}
		else if (action.equals("filmModeLounge")) {
			if (patioDoor.isTriggered()) {
				log.info("Request for film mode in Lounge and Kitchen but patio door is open [" + activeUser + "]");
				out.print("Close patio door");
			}
			else {
				log.info("Request for film mode in Lounge and Kitchen, turning lamps off, LED on and blinds closed [" + activeUser + "]");
				if (!"0".equals(loungeWindowBlind.getDeviceLevel())) {
					int windowBlindMovementTime = CommonQueries.calculateBlindMovementTime(loungeWindowBlind, "0");
					successfulCall = loungeWindowBlind.turnDeviceOn(true);
					
					int patioBlindMovementTime = CommonQueries.calculateBlindMovementTime(loungePatioBlind, "0");
					if (successfulCall) {
						successfulCall = loungePatioBlind.turnDeviceOn(true);
					}
					
					if (patioBlindMovementTime > windowBlindMovementTime) {
						windowBlindMovementTime = patioBlindMovementTime;
					}
					
					try {
						Thread.sleep(windowBlindMovementTime);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				
				successfulCall = lampOneLounge.turnDeviceOff(true);
				lampOneLounge.resetAutoOverridden();
				if (successfulCall) {
					successfulCall = lampTwoLounge.turnDeviceOff(true);
					lampTwoLounge.resetAutoOverridden();
					if (successfulCall) {
						successfulCall = ledRodLounge.turnDeviceOn(true);
						ledRodLounge.resetAutoOverridden();
					}
				}
				
				if (successfulCall) {
					out.print("Film mode now on in lounge");
				}
				else {
					out.print("Issue turning film mode on");
				}
			}
		}
		else if (action.equals("offLounge")) {
			log.info("Request for all lights off and blinds up (resetting manual flag) in Lounge and Kitchen [" + activeUser + "]");
			for (ControllableDevice device : devicesInLoungeAndKitchen) {
				if (!(device instanceof Blind)) {
					successfulCall = device.turnDeviceOff(true);
					device.resetAutoOverridden();
					if (!successfulCall) {
						break;
					}
				}
			}
			
			if (loungeWindowBlind.isDeviceOn()) {
				loungeWindowBlind.resetManuallyOverridden();
			}
			
			if (loungePatioBlind.isDeviceOn()) {
				loungePatioBlind.resetManuallyOverridden();
			}
			
			boolean blindsToOpen = false;
			if (!CommonQueries.isBrightnessAt0()) {
				blindsToOpen = true;
			}
			
			if (successfulCall) {
				if (blindsToOpen) {
					out.print("Lights now off, blinds will open soon");
				}
				else {
					out.print("Lights now off in lounge");
				}
			}
			else {
				out.print("Issue turning lights off / blinds up");
			}
		}
		else if (action.equals("blindToggle")) {
			if (loungeWindowBlind.isDeviceOn() || loungePatioBlind.isDeviceOn()) {
				log.info("Request for blinds fully open in lounge [" + activeUser + "]");
				successfulCall = loungeWindowBlind.turnDeviceOff(true);
				loungeWindowBlind.resetAutoOverridden();
				
				if (successfulCall) {
					successfulCall = loungePatioBlind.turnDeviceOff(true);
					loungePatioBlind.resetAutoOverridden();
				}
				
				if (successfulCall) {
					out.print("Blinds now opening in lounge");
				}
			}
			else {
				log.info("Request for blinds fully closed in lounge [" + activeUser + "]");
				successfulCall = loungeWindowBlind.turnDeviceOn(true);
				loungeWindowBlind.resetAutoOverridden();
				
				if (successfulCall) {
					successfulCall = loungePatioBlind.turnDeviceOn(true);
					loungePatioBlind.resetAutoOverridden();
				}
				
				if (successfulCall) {
					out.print("Blinds now closing in lounge");
				}
			}
		}
		else if (action.equals("blindTiltToggle")) {
			log.info("Request for blind tilt in lounge [" + activeUser + "]");
			if (!CommonQueries.isBrightnessAt0()) {
				if (!loungeWindowBlind.isTilted() || !loungePatioBlind.isTilted()) {
					successfulCall = loungeWindowBlind.tiltBlindDown();
					successfulCall = loungePatioBlind.tiltBlindDown();
					if (successfulCall) {
						out.print("Blinds tilted down in lounge");
						log.info("Blinds tilted down in lounge [" + activeUser + "]");
					}
					else {
						out.print("Issue tilting blinds");
					}
				}
				else {
					successfulCall = loungeWindowBlind.tiltBlindUp();
					successfulCall = loungePatioBlind.tiltBlindUp();
					if (successfulCall) {
						out.print("Blinds tilted back up in lounge");
						log.info("Blinds tilted up in lounge [" + activeUser + "]");
					}
					else {
						out.print("Issue tilting blinds");
					}
				}
			}
			else {
				out.print("Too dark outside");
			}
		}
		else if (action.equals("bedroomModeLounge")) {
			String loungeBedroomMode = HomeAutomationProperties.getProperty("LoungeBedroomMode");
			if (loungeBedroomMode != null && "false".equals(loungeBedroomMode)) {
				HomeAutomationProperties.setOrUpdateProperty("LoungeBedroomMode", "true");
				log.info("Request for full bedroom mode in Lounge [" + activeUser + "]");
				out.print("Lounge now in bedroom mode");
			}
			else {
				HomeAutomationProperties.setOrUpdateProperty("LoungeBedroomMode", "false");
				log.info("Request for normal bedroom mode in Lounge [" + activeUser + "]");
				out.print("Lounge back to lounge mode");
			}
		}
		else if (action.equals("atHomeModeLounge")) {
			String atHomeModeLounge = HomeAutomationProperties.getProperty("AtHomeTodayMode");
			if (atHomeModeLounge != null && "false".equals(atHomeModeLounge)) {
				if (CommonQueries.isApartmentOccupied()) {
					HomeAutomationProperties.setOrUpdateProperty("AtHomeTodayMode", "true");
					log.info("Request for 'At Home Today' mode for full apartment [" + activeUser + "]");
					out.print("'At Home Today' mode now on");
				}
				else {
					log.info("Request for 'At Home Today' mode for full apartment but apartment has to be occupied [" + activeUser + "]");
					out.print("Apartment has to be occupied");
				}
			}
			else {
				HomeAutomationProperties.setOrUpdateProperty("AtHomeTodayMode", "false");
				log.info("Request for 'Normal Occupancy' mode for full apartment [" + activeUser + "]");
				out.print("'Normal Occupancy' mode now on");
			}
		}
		
		//response.setContentType("application/json");
		//response.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		//String json = "{online: 'true'}";
		out.flush();
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		RequestDispatcher dispatch = request.getRequestDispatcher("index.html");
		dispatch.forward(request, response);
	}

}
