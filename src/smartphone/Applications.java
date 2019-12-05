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
			
			AgentController ticker = myContainer.createNewAgent("ticker", TickerAgent.class.getCanonicalName(), null);
			ticker.start();
			
			AgentController manufacturer = myContainer.createNewAgent("manufacturer", ManufactureAgent.class.getCanonicalName(), null);
			manufacturer.start();
			
			
			//suppliers
			Storage storage1 = new Storage();
			storage1.setSize(64);
			storage1.setItemID(3);
			Storage storage2 = new Storage();
			storage2.setSize(256);
			storage2.setItemID(4);
			
			Screen screen1 = new Screen();
			screen1.setSize(5);
			screen1.setItemID(1);
			Screen screen2 = new Screen();
			screen2.setSize(7);
			screen2.setItemID(2);
			
			Battery battery1 = new Battery();
			battery1.setSize(2000);
			battery1.setItemID(5);
			Battery battery2 = new Battery();
			battery2.setSize(3000);
			battery2.setItemID(6);
			
			Memory ram1 = new Memory();
			ram1.setSize(4);
			ram1.setItemID(7);
			Memory ram2 = new Memory();
			ram2.setSize(8);
			ram2.setItemID(8);
				
			Item[] components1 = {screen1, screen2, storage1, storage2, ram1, ram2, battery1, battery2};
			int[] prices1 = {100, 150, 25, 50, 30, 60, 70, 100};
			int deliverySpeed1 = 1;
			
			Object[] supplier1List = 
				{
						components1,
						prices1,
						deliverySpeed1
				};
			
			AgentController supplier1 = myContainer.createNewAgent("supplier1", SupplierAgent.class.getCanonicalName(), supplier1List);
			supplier1.start();
			
			
			Item[] components2 = {storage1, storage2, ram1, ram2};
			int[] prices2 = {15, 40, 20, 35};
			int deliverySpeed2 = 4;
			
			Object[] supplier2List =
				{
						components2,
						prices2,
						deliverySpeed2
				};
			AgentController supplier2 = myContainer.createNewAgent("supplier2", SupplierAgent.class.getCanonicalName(), supplier2List);
			supplier2.start();
			
			
			
			//customers 
			AgentController customer1 = myContainer.createNewAgent("customer1", CustomerAgent.class.getCanonicalName(), null);
			customer1.start();
			
			AgentController customer2 = myContainer.createNewAgent("customer2", CustomerAgent.class.getCanonicalName(), null);
			customer2.start();

			AgentController customer3 = myContainer.createNewAgent("customer3", CustomerAgent.class.getCanonicalName(), null);
			customer3.start();
			
		}
		catch (Exception e)
		{
			System.out.println("Exception starting agent: " + e.toString());
		}
	}
}