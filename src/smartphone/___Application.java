package smartphone;

import jade.core.*;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class ___Application {

	public static void main (String[] args) {
		
		Profile myProfile = new ProfileImpl();
		Runtime myRuntime = Runtime.instance();
		try {
			ContainerController myContainer = myRuntime.createMainContainer(myProfile);
			
			AgentController rma = myContainer.createNewAgent("rma", "jade.tools.rma.rma", null);
			rma.start();
			
			AgentController tickerAgent = myContainer.createNewAgent("Ticker", ___TickerAgent.class.getCanonicalName(), null);
			tickerAgent.start();
			
			AgentController manufacturerAgent = myContainer.createNewAgent("Manufacturer", ___ManufacturerAgent.class.getCanonicalName(), null);
			manufacturerAgent.start();
			
			AgentController supplierAgent1 = myContainer.createNewAgent("Supplier1", ___SupplierAgent.class.getCanonicalName(), null);
			supplierAgent1.start();
			
			AgentController supplierAgent2 = myContainer.createNewAgent("Supplier2", ___SupplierAgent.class.getCanonicalName(), null);
			supplierAgent2.start();
			
			AgentController customerAgent1 = myContainer.createNewAgent("Customer1", ___CustomerAgent.class.getCanonicalName(), null);
			customerAgent1.start();
			
			AgentController customerAgent2 = myContainer.createNewAgent("Customer2", ___CustomerAgent.class.getCanonicalName(), null);
			customerAgent2.start();
			
			AgentController customerAgent3 = myContainer.createNewAgent("Customer3", ___CustomerAgent.class.getCanonicalName(), null);
			customerAgent3.start();

		}
		catch(Exception e){
			System.out.println("Exception starting agent: " + e.toString());
		}
	}
}
