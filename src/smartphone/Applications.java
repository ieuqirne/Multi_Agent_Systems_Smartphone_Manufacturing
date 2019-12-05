package smartphone;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import smartphone_ontology.elements.Battery;
import smartphone_ontology.elements.Item;
import smartphone_ontology.elements.Memory;
import smartphone_ontology.elements.Screen;
import smartphone_ontology.elements.Storage;


public class Applications {

	public static void main(String[] args)
	{
		Profile myProfile = new ProfileImpl();
		Runtime myRuntime = Runtime.instance();
		try
		{
			ContainerController myContainer = myRuntime.createMainContainer(myProfile);
			AgentController rma = myContainer.createNewAgent("rma", "jade.tools.rma.rma", null);
			rma.start();
			
			AgentController ticker = myContainer.createNewAgent("Ticker", TickerAgent.class.getCanonicalName(), null);
			ticker.start();
			
			AgentController manufacturer = myContainer.createNewAgent("Manufacturer", ManufactureAgent.class.getCanonicalName(), null);
			manufacturer.start();
						
			AgentController supplier1 = myContainer.createNewAgent("Supplier_1", SupplierAgent.class.getCanonicalName(), null);
			supplier1.start();
			
			AgentController supplier2 = myContainer.createNewAgent("Supplier_2", SupplierAgent.class.getCanonicalName(), null);
			supplier2.start();
						
			AgentController customer1 = myContainer.createNewAgent("Customer_1", CustomerAgent.class.getCanonicalName(), null);
			customer1.start();
			
			AgentController customer2 = myContainer.createNewAgent("Customer_2", CustomerAgent.class.getCanonicalName(), null);
			customer2.start();

			AgentController customer3 = myContainer.createNewAgent("Customer_3", CustomerAgent.class.getCanonicalName(), null);
			customer3.start();
			
		}
		catch (Exception e)
		{
			System.out.println("Exception starting agent: " + e.toString());
		}
	}
}