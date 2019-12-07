package smartphone;

import java.util.ArrayList;
import java.util.HashMap;

import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import smartphone_ontology.elements.Battery;
import smartphone_ontology.elements.Buy;
import smartphone_ontology.elements.Deliver;
import smartphone_ontology.elements.Item;
import smartphone_ontology.elements.Memory;
import smartphone_ontology.elements.Order;
import smartphone_ontology.elements.Screen;
import smartphone_ontology.elements.Sell;
import smartphone_ontology.elements.Storage;
import smartphones_ontology.SmartphoneOntology;


public class ManufactureAgent extends Agent
{
	private Codec codec = new SLCodec();
	private Ontology ontology = SmartphoneOntology.getInstance();
	
	private ArrayList<AID> customersAgent = new ArrayList<>();
	private ArrayList<AID> suppliersAgent = new ArrayList<>();
	private ArrayList<Order> workingOrders = new ArrayList<>();
	private ArrayList<Order> ordersToAssembly = new ArrayList<>();
	private ArrayList<Order>  workingOrdersToBuyScreBatt= new ArrayList<>();//workingOrders
	private ArrayList<Order> lateOrders = new ArrayList<>();
	private ArrayList<Sell> openDeliveries = new ArrayList<>();
	private HashMap<Item, Integer> toBuy1 = new HashMap<>();//compToOrderFromSupplier
	private HashMap<Item, Integer> toBuy2 = new HashMap<>();
	private HashMap<Integer, Integer> warehouseStock = new HashMap<>();//StockInWarehouse
	private Order currentOrder = new Order();
	private AID tickerAgent;
	private int day = 1;
	private int ordersSent = 0;
	private int warehouseStorageCost = 5;
	private int componentCost = 0; // 
	private int orderPayment = 0;
	private int totalProfit;
	private int assemblyMax = 50;
	private int [] smartphoneDayToAssembly = new int[140];
	

	//private HashMap<Integer, Long> smartphoneToAssembly = new HashMap<>();//StockInWarehouse


	@Override 
	protected void setup()
	{
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		
		//add agent to yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Manufacturer");
		sd.setName(getLocalName() + "-Manufacturer-Agent");
		dfd.addServices(sd);
		try
		{
			DFService.register(this, dfd);
		}
		catch (FIPAException e)
		{
			e.printStackTrace();
		}
		
		
		for(int x = 0; x <= TickerAgent.NUM_DAYS; x++) {
			smartphoneDayToAssembly[x] = 0;
		}
		
		
		
		addBehaviour(new TickerWaiter(this));
	}
	
	protected void takedown()
	{
		try
		{
			DFService.deregister(this);
		}
		catch (FIPAException e)
		{
			e.printStackTrace();
		}
	}
	
	public class TickerWaiter extends CyclicBehaviour
	{
		public TickerWaiter(Agent a)
		{
			super(a);
		}
		
		@Override
		public void action()
		{
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("NewDay"),
					MessageTemplate.MatchContent("terminate"));
			ACLMessage msgTicker = myAgent.receive(mt);
			if(msgTicker != null)
			{
				if(tickerAgent == null)
				{
					tickerAgent = msgTicker.getSender();
				}
				if(msgTicker.getContent().equals("NewDay"))
				{
					System.out.println("\nDay " + day + "| ");
					SequentialBehaviour dailyActivity = new SequentialBehaviour();
					dailyActivity.addSubBehaviour(new FindCustomersAndSuppliers(myAgent));
					dailyActivity.addSubBehaviour(new ReceiveCustomerOrders(myAgent));
					dailyActivity.addSubBehaviour(new RequestComponentsSupplier());
					dailyActivity.addSubBehaviour(new BuyComponentsToSuppliers());
					dailyActivity.addSubBehaviour(new GetComponentsFromSuppliers());
					dailyActivity.addSubBehaviour(new GetDeliveriesFromSuppliers());
					dailyActivity.addSubBehaviour(new AssemblySmartphones());
					dailyActivity.addSubBehaviour(new GetPaymentsFromSuppliers());
					dailyActivity.addSubBehaviour(new GetProfit());
					dailyActivity.addSubBehaviour(new EndDay(myAgent));
					
					myAgent.addBehaviour(dailyActivity);
					//day++;
				}
				else
				{
					myAgent.doDelete();
				}
			}
			else
			{
				block();
			}
		}
	}	
	
	
	public class FindCustomersAndSuppliers extends OneShotBehaviour
	{
		public FindCustomersAndSuppliers(Agent a)
		{
			super(a);
		}
		
		@Override 
		public void action()
		{
			DFAgentDescription customerTemplate = new DFAgentDescription();
			ServiceDescription csd = new ServiceDescription();
			csd.setType("Customer");
			customerTemplate.addServices(csd);
			
			DFAgentDescription supplierTemplate = new DFAgentDescription();
			ServiceDescription ssd = new ServiceDescription();
			ssd.setType("Supplier");
			supplierTemplate.addServices(ssd);
			
			try
			{
				customersAgent.clear();
				DFAgentDescription[] custAgent = DFService.search(myAgent, customerTemplate);
				for(int i = 0; i<custAgent.length; i++)
				{
					customersAgent.add(custAgent[i].getName());
				}
				//System.out.println("Number Supplier Agents: " + customersAgent.size());
				suppliersAgent.clear();
				DFAgentDescription[] supplierAgent = DFService.search(myAgent, supplierTemplate);
				for(int i = 0; i<supplierAgent.length; i++)
				{
					suppliersAgent.add(supplierAgent[i].getName());
				}
				//System.out.println("Number Supplier Agents: " + suppliersAgent.size());
			}
			catch (FIPAException fe)
			{
				fe.printStackTrace();
			}
		}
	}
	
	/*
	 * The ReceiveCustomerOrders behaviour receives all order proposals from the customerAgents
	 * it decides which to accept (based on highest price) and adds that order to the 
	 * openOrder list. each customer is either sent a REFUSE or ACCEPT_PROPOSAL reply.
	 */
	public class ReceiveCustomerOrders extends Behaviour
	{

		private int numOrders;
		private Order bestOrder;
		private ArrayList<Order> acceptedOrders = new ArrayList<>();
		private ArrayList<Order> rejectedOrders = new ArrayList<>();
		
		int totalQ;
		public ReceiveCustomerOrders(Agent a)
		{
			super(a);
		}
		
		@Override
		public void action() 
		{
			MessageTemplate mt2 = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage order2 = myAgent.receive(mt2);
			
			if(order2 != null)
			{
				numOrders++;
				try
				{
					ContentElement ce2 = null;
					
					ce2 = getContentManager().extractContent(order2);
					if(ce2 instanceof Action)
					{
						Concept action = ((Action)ce2).getAction();
						if(action instanceof Order)
						{
							
							Order custOrder = (Order)action;
							System.out.println("\nDay " + day);
							System.out.println("Due: " + custOrder.getDueDate());
							int dueToDeliver = custOrder.getDueDate();
							System.out.println("Due to Deliver: " + dueToDeliver);
							int numberToDeliver = smartphoneDayToAssembly[dueToDeliver];
							System.out.println("Day to numberToDeliver: " + numberToDeliver);
							int quantityInOrder = custOrder.getQuantity();
							System.out.println("quantityInOrder: " + quantityInOrder);
							System.out.println("getOrderID: " + custOrder.getOrderID());
							/*
							 * If DueDate is smaller than 4 days and the numberToDeliver + quantityInOrder < 50. The Delivery will be assembly on DueDate.
							 * If DueDate is bigger than 4 days. A ForLoop start ActualDay+4 to try to a find the closest day that it can be delivered
							 * 
							 */
							//HEREHEREHEREHEREHEREHEREHERE
							if(dueToDeliver - day < 5) {
								if(numberToDeliver +  quantityInOrder <= assemblyMax) {
									totalQ = smartphoneDayToAssembly[dueToDeliver] + quantityInOrder;
									System.out.println("totalQ: " + totalQ);
									
									smartphoneDayToAssembly[dueToDeliver] = totalQ;
									/*smartphoneToAssembly.put(dueToDeliver, custOrder.getOrderID());*/
									custOrder.setAssemblyDay(dueToDeliver);
									//workingOrders.add(custOrder);
									acceptedOrders.add(custOrder);
											
								}else {
									rejectedOrders.add(custOrder);
									System.out.println("Reject Order, Too many to assamb that day");
									
								}
							}else {
								
								for(int x = day + 4; x <= dueToDeliver ; x++) {
									numberToDeliver = smartphoneDayToAssembly[x];
									if(x > dueToDeliver) {
										rejectedOrders.add(custOrder);
										System.out.println("Reject it because there is not Possibility of Deliver It before due Date");
									}
									if(numberToDeliver +  quantityInOrder <= assemblyMax) {
										System.out.println("Could be added on day: " + x);
										totalQ = smartphoneDayToAssembly[x] + quantityInOrder;
										smartphoneDayToAssembly[x] = totalQ;
										//smartphoneToAssembly.put(x, custOrder.getOrderID());
										custOrder.setAssemblyDay(x);
										//workingOrders.add(custOrder);
										acceptedOrders.add(custOrder);
										break;
									}
									//smartphoneToAssembly.containsValue(value)
								}
							}
							
							for(int x = 1; x <= TickerAgent.NUM_DAYS; x++) {
								System.out.print(smartphoneDayToAssembly[x] +",");
							}
							System.out.println();
							//System.out.println(smartphoneToAssembly);
						}
					}
					
					
				}catch (CodecException ce){
					ce.printStackTrace();
				}catch (OntologyException oe){
					oe.printStackTrace();
				}
				
			}else 
			{
				block();
			}
				
			
			/*for(int x = 0; x < workingOrders.size(); x++) {
				System.out.println("Order ID: " + workingOrders.get(x).getOrderID() + "Assembly Day: " + workingOrders.get(x).getAssemblyDay());
			}*/
			
			
			if(numOrders == customersAgent.size())
			{
				//System.out.println("Accepted Orders");
				for(int x = 0; x < acceptedOrders.size(); x++) {
					workingOrders.add(acceptedOrders.get(x));
					
					ACLMessage accepted = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
					accepted.setLanguage(codec.getName());
					accepted.setOntology(ontology.getName());
					accepted.addReceiver(acceptedOrders.get(x).getPurchaser());
					accepted.setConversationId("ManufactureAnswerToCustomer");
					//System.out.println(accepted);
					Order ord = acceptedOrders.get(x);
					
					Action sendReply = new Action();
					sendReply.setAction(ord);
					sendReply.setActor(acceptedOrders.get(x).getPurchaser());
					
					try{
						getContentManager().fillContent(accepted, sendReply);
						send(accepted);
					}catch (CodecException ce){
						ce.printStackTrace();
					}catch (OntologyException oe){
						oe.printStackTrace();
					} 
					
					
				}
				
				
				System.out.println("Rejected Orders");
				for(int x = 0; x < rejectedOrders.size(); x++) {
					ACLMessage rejected = new ACLMessage(ACLMessage.REFUSE);
					rejected.setLanguage(codec.getName());
					rejected.setOntology(ontology.getName());
					rejected.addReceiver(rejectedOrders.get(x).getPurchaser());
					rejected.setConversationId("ManufactureAnswerToCustomer");
					//System.out.println(rejected);
					Order ord = rejectedOrders.get(x);
					
					Action sendReply = new Action();
					sendReply.setAction(ord);
					sendReply.setActor(rejectedOrders.get(x).getPurchaser());
					
					try{
						getContentManager().fillContent(rejected, sendReply);
						send(rejected);
					}catch (CodecException ce){
						ce.printStackTrace();
					}catch (OntologyException oe){
						oe.printStackTrace();
					} 
				}	
			}
		}

		@Override
		public boolean done() 
		{
			return numOrders == customersAgent.size();
		}
		
	}
	
	
	public class RequestComponentsSupplier extends OneShotBehaviour
	{
		int ordersToSend = 0;
		int ordersSent = 0;
		@Override
		public void action() 
		{
			//System.out.println("Inside RequestComponentsSupplier");
			//System.out.println("workingOrders.size() "+ workingOrders.size());
			for(int x = 0; x < workingOrders.size(); x++) {
				
				System.out.println(workingOrders.get(x).getOrderID());
				System.out.println(workingOrders.get(x).getAssemblyDay());
				Storage storage = workingOrders.get(x).getSmartphone().getStorage();
				Battery battery = workingOrders.get(x).getSmartphone().getBattery();
				Screen screen = workingOrders.get(x).getSmartphone().getScreen();
				Memory memory = workingOrders.get(x).getSmartphone().getMemory();
				int quantity = workingOrders.get(x).getQuantity();
				
				//I need the stock for tomorrow
				if(workingOrders.get(x).getAssemblyDay() - day <= 1)
				{
					ordersToSend ++;
					//System.out.println("One order with workingOrders.get(x).getAssemblyDay() - day <= 1");
					if(toBuy1.containsKey(screen)){
						toBuy1.put(screen, (toBuy1.get(screen) + quantity));
					}else{
						toBuy1.put(screen, quantity);
					}
					if(toBuy1.containsKey(memory)){
						toBuy1.put(memory, (toBuy1.get(memory) + quantity));
					}else{
						toBuy1.put(memory, quantity);
					}
					if(toBuy1.containsKey(storage)){
						toBuy1.put(storage, (toBuy1.get(storage) + quantity));
					}else{
						toBuy1.put(storage, quantity);
					}
					if(toBuy1.containsKey(battery)){
						toBuy1.put(battery, (toBuy1.get(battery) + quantity));
					}else{
						toBuy1.put(battery, quantity);
					}	
					ordersToAssembly.add(workingOrders.get(x));
					workingOrders.remove(x);
				}
			}

		}
	}
	
	public class BuyComponentsToSuppliers extends Behaviour
	{
		int sent = 0;
		AID supplier1;
		AID supplier2;
		public void action()
		{
			System.out.println("Inside BuyComponentsToSuppliers");	
	
			
			for(int x = 0; x < suppliersAgent.size(); x++) {
				//System.out.println(suppliersAgent.get(x).getName());
				if(suppliersAgent.get(x).getName().contains("Supplier_1")) {
					supplier1 = suppliersAgent.get(x);
					//System.out.println("Inside");
				}else {
					supplier2 = suppliersAgent.get(x);
					//System.out.println("Outside");
				}
				
			}
			for(Item key : toBuy1.keySet())
			{
				//System.out.println("Item key : toBuy1.keySet()");

				ACLMessage msgBuyCompSupp1 = new ACLMessage(ACLMessage.PROPOSE);
				msgBuyCompSupp1.setLanguage(codec.getName());
				msgBuyCompSupp1.setOntology(ontology.getName());
				msgBuyCompSupp1.addReceiver(supplier1);

				Sell sell = new Sell();
				sell.setBuyer(getAID());
				sell.setItem(key);
				sell.setQuantity(toBuy1.get(key));
				
				Action myOrder = new Action();
				myOrder.setAction(sell);
				myOrder.setActor(myAgent.getAID());
				

				try {
					getContentManager().fillContent(msgBuyCompSupp1, myOrder);
				} catch (CodecException | OntologyException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				send(msgBuyCompSupp1);
				sent++;

			}
			for(Item key : toBuy2.keySet())
			{
				ACLMessage msgBuyCompSupp2 = new ACLMessage(ACLMessage.PROPOSE);
				msgBuyCompSupp2.setLanguage(codec.getName());
				msgBuyCompSupp2.setOntology(ontology.getName());
				msgBuyCompSupp2.addReceiver(supplier2);

				Sell sell = new Sell();
				sell.setBuyer(getAID());
				sell.setItem(key);
				sell.setQuantity(toBuy2.get(key));
				
				
				Action myOrder = new Action();
				myOrder.setAction(sell);
				myOrder.setActor(myAgent.getAID());
				
				
				try {
					getContentManager().fillContent(msgBuyCompSupp2, myOrder);
				} catch (CodecException | OntologyException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				send(msgBuyCompSupp2);
				sent++;
			}
			
			
			
		}
		public boolean done()
		{
			return sent == toBuy1.size() + toBuy2.size();	
		}
	}
	
	public class BuyComponentsSuppliers extends Behaviour
	{
		int noReplies = 0;
		
		public void action()
		{
			
			System.out.println("Inside BuyComponentsSuppliers");

			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.CFP),
					MessageTemplate.MatchPerformative(ACLMessage.REFUSE));
			ACLMessage msgBuyComSupp = myAgent.receive(mt);
			if(msgBuyComSupp!=null)
			{
				System.out.println("msgBuyComSupp");
				System.out.println(msgBuyComSupp);
				noReplies++;
				if(msgBuyComSupp.getPerformative() == ACLMessage.CFP)
				{
					try
					{
						ContentElement ce = null;

						ce = getContentManager().extractContent(msgBuyComSupp);
						if(ce instanceof Buy)
						{
							Buy buy = (Buy) ce;
							System.out.println("Products ID:  " + buy.getItem().getItemID() + " Type: " + buy.getItem().getClass()) ;
							//if the component is a screen or a battery supplier 1 will be supplier
							
							if(buy.getItem().getItemID() <=2 || buy.getItem().getItemID() >= 7 )
							{
								ACLMessage msgBuyCompSupp1 = new ACLMessage(ACLMessage.PROPOSE);
								msgBuyCompSupp1.setLanguage(codec.getName());
								msgBuyCompSupp1.setOntology(ontology.getName());
								msgBuyCompSupp1.addReceiver(buy.getOwner());

								Sell sell = new Sell();
								sell.setBuyer(getAID());
								sell.setItem(buy.getItem());
								sell.setQuantity(currentOrder.getQuantity());
								
								Action myOrder = new Action();
								myOrder.setAction(sell);
								myOrder.setActor(buy.getOwner());
								

								getContentManager().fillContent(msgBuyCompSupp1, myOrder);
								send(msgBuyCompSupp1);

	
							}
							//if the due date is in 4 or more days use supplier 2
							else if(buy.getShipmentSpeed() == 4 && ((currentOrder.getDueDate() - day) >= 4))
							{
								ACLMessage msgBuyCompSupp2 = new ACLMessage(ACLMessage.PROPOSE);
								msgBuyCompSupp2.setLanguage(codec.getName());
								msgBuyCompSupp2.setOntology(ontology.getName());
								msgBuyCompSupp2.addReceiver(buy.getOwner());

								
								Sell sell = new Sell();
								sell.setBuyer(getAID());
								sell.setItem(buy.getItem());
								sell.setQuantity(currentOrder.getQuantity());
								
								Action myOrder = new Action();
								myOrder.setAction(sell);
								myOrder.setActor(buy.getOwner());
								

								getContentManager().fillContent(msgBuyCompSupp2, myOrder);
								send(msgBuyCompSupp2);

							}
							//if the due date is in less than 4 days use supplier 1
							else if(buy.getShipmentSpeed() == 1 && ((currentOrder.getDueDate() - day) < 4))
							{
								ACLMessage msgBuyCompSupp1 = new ACLMessage(ACLMessage.PROPOSE);
								msgBuyCompSupp1.addReceiver(buy.getOwner());
								msgBuyCompSupp1.setLanguage(codec.getName());
								msgBuyCompSupp1.setOntology(ontology.getName());
								
								Sell sell = new Sell();
								sell.setBuyer(getAID());
								sell.setItem(buy.getItem());
								sell.setQuantity(currentOrder.getQuantity());
								
								Action myOrder = new Action();
								myOrder.setAction(sell);
								myOrder.setActor(buy.getOwner());
								
							
								getContentManager().fillContent(msgBuyCompSupp1, myOrder);
								send(msgBuyCompSupp1);
								
							}
							
						}
					}
					catch (CodecException ce) {
						ce.printStackTrace();
					}
					catch (OntologyException oe) {
						oe.printStackTrace();
					}
				}
			}
			else
			{
				block();
			}
			
			//System.out.println("noReplies: " + noReplies);
			//System.out.println("toBuy1: " + toBuy1.size());
			//System.out.println("toBuy2: " + toBuy2.size());	
			
		}

		
		public boolean done()
		{
			return noReplies == toBuy1.size() + toBuy2.size();		
		}
	}
	
	
	public class GetComponentsFromSuppliers extends Behaviour
	{
		
		int noReplies = 0;
		public void action()
		{
			System.out.println("Inside GetComponentsFromSuppliers");
			System.out.println("toBuy1 " + toBuy1.size());
			System.out.println("toBuy2 " + toBuy2.size());
			//System.out.println("Inside GetComponentsFromSuppliers");
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchPerformative(ACLMessage.FAILURE));
			ACLMessage msgGetCompSup = myAgent.receive(mt);
			System.out.println(msgGetCompSup);
			if(msgGetCompSup != null)
			{
				System.out.println("msgGetCompSup" + msgGetCompSup);
				noReplies++;
				if(msgGetCompSup.getPerformative() == ACLMessage.INFORM)
				{
					try
					{
						ContentElement ce = null;
						
						ce = getContentManager().extractContent(msgGetCompSup);
						if(ce instanceof Action)
						{
							Concept action = ((Action)ce).getAction();
							if(action instanceof Sell)
							{
								Sell order = (Sell)action;
								
								openDeliveries.add(order);
								componentCost += order.getPrice();
								
								System.out.println("Components purchased! " + order.getQuantity() + " x " + order.getItem().getClass().getName() + " purchased from " + msgGetCompSup.getSender().getName());
								System.out.println("Cost: " + order.getPrice());
							}
						}
						
						
					}
					catch (CodecException ce) 
					{
						ce.printStackTrace();
					}
					catch (OntologyException oe) 
					{
						oe.printStackTrace();
					}
				}
			}
			else
			{
				block();
			}
			if(toBuy1.size() == 0 && toBuy2.size() == 0)
			{
				return;
			}
		}
		
		public boolean done()
		{
			return noReplies == toBuy1.size() + toBuy2.size();
		}
	}
	
	
	public class GetDeliveriesFromSuppliers extends OneShotBehaviour
	{

		public void action() 
		{
			System.out.println("Inside GetDeliveriesFromSuppliers");
			System.out.println("Open Deliveries: " + openDeliveries);
			if(!openDeliveries.isEmpty())
			{
				
				for(Sell order : openDeliveries)
				{
					//System.out.println("Item ID: " +order.getItem().getItemID() + "Ordersssss: " +order.getQuantity());

					if(warehouseStock.containsKey(order.getItem().getItemID()))
					{
						int quantity = warehouseStock.get(order.getItem().getItemID());
						warehouseStock.put(order.getItem().getItemID(), quantity + order.getQuantity());
						//System.out.println("Order of " + order.getQuantity() + " x " + order.getItem() + " added to Stocklist");
					}
					else
					{
						warehouseStock.put(order.getItem().getItemID(), order.getQuantity());
						//System.out.println("Order of " + order.getQuantity() + " x " + order.getItem() + " added to Stocklist");
					}	
				}
			}
			System.out.println("Stock Warehouse: " + warehouseStock);
			//System.out.println(warehouseStock.keySet());
			//System.out.println(warehouseStock.values());
			System.out.println("");
		}
		
	}
	
	
	public class AssemblySmartphones extends OneShotBehaviour
	{
		
		public void action() 
		{
			System.out.println("Inside AssemblySmartphones");
			System.out.println("ordersToAssembly " + ordersToAssembly.size());
			System.out.println( ordersToAssembly);
			
			for(int i = 0; i < ordersToAssembly.size(); i++)
			{
				if(ordersToAssembly.get(i).getAssemblyDay() >= day)
				{
					System.out.println("Order to Assembly Today: " + ordersToAssembly.get(i).getOrderID());
					System.out.println("Day: " + day);
					System.out.println("Purchaser: " + ordersToAssembly.get(i).getPurchaser());
					System.out.println("Assemble Day: " + ordersToAssembly.get(i).getAssemblyDay());
					System.out.println("getQuantity: " + ordersToAssembly.get(i).getQuantity());
					System.out.println("getMemory: " + ordersToAssembly.get(i).getSmartphone().getMemory().getItemID());
					System.out.println("getBattery: " + ordersToAssembly.get(i).getSmartphone().getBattery().getItemID());
					System.out.println("getScreen: " + ordersToAssembly.get(i).getSmartphone().getScreen().getItemID());
					System.out.println("getStorage: " + ordersToAssembly.get(i).getSmartphone().getStorage().getItemID());
					
					System.out.println("WareHouse: " + warehouseStock);
					
					
					System.out.println("Due Day" + ordersToAssembly.get(i).getDueDate());
					
					Order o = ordersToAssembly.get(i);
					int screen = ordersToAssembly.get(i).getSmartphone().getScreen().getItemID();
					int battery = ordersToAssembly.get(i).getSmartphone().getBattery().getItemID();
					int memory = ordersToAssembly.get(i).getSmartphone().getMemory().getItemID();
					int storage = ordersToAssembly.get(i).getSmartphone().getStorage().getItemID();
					try
					{
						if(warehouseStock.get(screen) >= ordersToAssembly.get(i).getQuantity() && warehouseStock.get(battery) >= ordersToAssembly.get(i).getQuantity()
								&& warehouseStock.get(memory) >= ordersToAssembly.get(i).getQuantity() && warehouseStock.get(storage) >= ordersToAssembly.get(i).getQuantity())
						{
		
							warehouseStock.put(screen, (warehouseStock.get(screen) - o.getQuantity()));
							warehouseStock.put(battery, (warehouseStock.get(battery) - o.getQuantity()));
							warehouseStock.put(memory, (warehouseStock.get(memory) - o.getQuantity()));
							warehouseStock.put(storage, (warehouseStock.get(storage) - o.getQuantity()));
	
							ACLMessage msg = new ACLMessage(ACLMessage.CONFIRM);
							msg.addReceiver(o.getPurchaser());
							msg.setLanguage(codec.getName());
							msg.setOntology(ontology.getName());
	
							Deliver deliver = new Deliver();
							deliver.setOrder(o);
							
							Action myDelivery = new Action();
							myDelivery.setAction(deliver);
							myDelivery.setActor(getAID());
							
							getContentManager().fillContent(msg, myDelivery);
							send(msg);
							
							ordersSent++;
							ordersToAssembly.remove(o);
							System.out.println("Order for " + o.getQuantity() + " x " + o.getSmartphone().getName() + "s sent to " + o.getPurchaser().getName());
	
						}
					}
					catch (CodecException ce) {
						ce.printStackTrace();
					}
					catch (OntologyException oe) {
						oe.printStackTrace();
					}
						doWait(1000);
					
					//Order o = ordersToAssembly.get(i);
				}
				

		
			}

	
			
			//if(day > 6) {
				//doWait(10000);
			//}
			
			
			
			
			
			
			/*
			
			if(ordersToAssembly.isEmpty() == false)
			{
				System.out.println("There is order to assembly");
				for(int i = 0; i < ordersToAssembly.size(); i++)
				{
					Order o = ordersToAssembly.get(i);
					if(o.getDueDate() < day)
					{
						lateOrders.add(o);
						ordersToAssembly.remove(o);
					}
				}
			}
			try
			{
				if(lateOrders.isEmpty() == false)
				{
					for(int i = 0; i < lateOrders.size(); i++)
					{
						Order o = lateOrders.get(i);
						int screen = o.getSmartphone().getScreen().getItemID();
						int battery = o.getSmartphone().getBattery().getItemID();
						int ram = o.getSmartphone().getMemory().getItemID();
						int storage = o.getSmartphone().getStorage().getItemID();
						
						if(o.getQuantity() < assemblyMax)
						{
							if(warehouseStock.containsKey(screen) && warehouseStock.containsKey(storage) && warehouseStock.containsKey(ram) && warehouseStock.containsKey(storage))
							{
								if(warehouseStock.get(screen) >= o.getQuantity() && warehouseStock.get(battery) >= o.getQuantity()
										&& warehouseStock.get(ram) >= o.getQuantity() && warehouseStock.get(storage) >= o.getQuantity())
								{
									//assemble phone order
									assemblyMax -= o.getQuantity();
									warehouseStock.put(screen, (warehouseStock.get(screen) - o.getQuantity()));
									warehouseStock.put(battery, (warehouseStock.get(battery) - o.getQuantity()));
									warehouseStock.put(ram, (warehouseStock.get(ram) - o.getQuantity()));
									warehouseStock.put(storage, (warehouseStock.get(storage) - o.getQuantity()));

									ACLMessage msgGetCompSup = new ACLMessage(ACLMessage.CONFIRM);
									msgGetCompSup.setLanguage(codec.getName());
									msgGetCompSup.setOntology(ontology.getName());
									msgGetCompSup.addReceiver(o.getPurchaser());

									
									Deliver deliver = new Deliver();
									deliver.setOrder(o);
									
									Action myDelivery = new Action();
									myDelivery.setAction(deliver);
									myDelivery.setActor(getAID());
									
									getContentManager().fillContent(msgGetCompSup, myDelivery);
									send(msgGetCompSup);
									
									ordersSent++;
									lateOrders.remove(o);
									System.out.println("Order for " + o.getQuantity() + " x " + o.getSmartphone().getName() + "s sent to " + o.getPurchaser().getName());

								}
							}
						}
					}
				}
				
				else if(ordersToAssembly.isEmpty() == false)
				{
					for(int i = 0; i < ordersToAssembly.size(); i++)
					{
						Order o = ordersToAssembly.get(i);
						int screen = o.getSmartphone().getScreen().getItemID();
						int battery = o.getSmartphone().getBattery().getItemID();
						int ram = o.getSmartphone().getMemory().getItemID();
						int storage = o.getSmartphone().getStorage().getItemID();
						
						if(o.getQuantity() < assemblyMax)
						{
							if(warehouseStock.containsKey(screen) && warehouseStock.containsKey(storage) && warehouseStock.containsKey(ram) && warehouseStock.containsKey(storage))
							{
						
								if(warehouseStock.get(screen) >= o.getQuantity() && warehouseStock.get(battery) >= o.getQuantity()
										&& warehouseStock.get(ram) >= o.getQuantity() && warehouseStock.get(storage) >= o.getQuantity())
								{
									//assemble phone order
									assemblyMax -= o.getQuantity();
									warehouseStock.put(screen, (warehouseStock.get(screen) - o.getQuantity()));
									warehouseStock.put(battery, (warehouseStock.get(battery) - o.getQuantity()));
									warehouseStock.put(ram, (warehouseStock.get(ram) - o.getQuantity()));
									warehouseStock.put(storage, (warehouseStock.get(storage) - o.getQuantity()));

									ACLMessage msgGetCompSup = new ACLMessage(ACLMessage.CONFIRM);
									msgGetCompSup.setLanguage(codec.getName());
									msgGetCompSup.setOntology(ontology.getName());
									msgGetCompSup.addReceiver(o.getPurchaser());


									Deliver deliver = new Deliver();
									deliver.setOrder(o);
									
									Action myDelivery = new Action();
									myDelivery.setAction(deliver);
									myDelivery.setActor(getAID());
									
									getContentManager().fillContent(msgGetCompSup, myDelivery);
									send(msgGetCompSup);
									
									ordersSent++;
									ordersToAssembly.remove(o);
									System.out.println("Order for " + o.getQuantity() + " x " + o.getSmartphone().getName() + "s sent to " + o.getPurchaser().getName());

								}
							}
						}
					}
				}
			}
			catch (CodecException ce) {
				ce.printStackTrace();
			}
			catch (OntologyException oe) {
				oe.printStackTrace();
			}*/
		}

	}
	
	
	public class GetPaymentsFromSuppliers extends Behaviour
	{
		int msgReceived = 0;
		
		public void action() 
		{
			MessageTemplate mt = MessageTemplate.MatchConversationId("PaymentFromCustomerToManu");
			ACLMessage order = myAgent.receive(mt);
			if(order!=null)
			{
				msgReceived++;
				if(order.getPerformative() == ACLMessage.INFORM)
				{

					try
					{
						int payment = Integer.parseInt(order.getContent());
						System.out.println("Payment of £" + payment + " received from " + order.getSender().getName());
						orderPayment += payment;
					}
					catch (NumberFormatException nfe)
					{
						nfe.printStackTrace();
					}
					
				}
			}
		}

		public boolean done() 
		{
			return ordersSent == msgReceived;
		}
		
	}
	
	
	public class GetProfit extends OneShotBehaviour
	{

		public void action() 
		{
			int lateFees = 0;
			int warehouseCost = 0;
			int dailyProfit = 0;
			System.out.println("");
			System.out.println("Today's profit calculation:");
			System.out.println("Total value of shipped orders: " + orderPayment);
			
			if(lateOrders.isEmpty())
			{
				System.out.println("Total cost of late fees: " + lateFees);
			}
			else
			{
				for(Order o : lateOrders)
				{
					lateFees += o.getDelayFee();
				}
				System.out.println("Total cost of late fees: " + lateFees);
			}
			
			if(warehouseStock.isEmpty())
			{
				System.out.println("Total cost of warehouse storage: " + warehouseCost);
			}
			else
			{
				for(Integer v : warehouseStock.values())
				{
					warehouseCost += (v * warehouseStorageCost);
				}
				System.out.println("Total cost of warehouse storage: " + warehouseCost);
			}
			
			System.out.println("Total cost of supplies purchased: " + componentCost);
			
			dailyProfit = orderPayment - lateFees - warehouseCost - componentCost;
			totalProfit += dailyProfit;
			
			System.out.println("Todays profit: " + dailyProfit);
			System.out.println("Total profit: " + totalProfit);
			
		}
		
	}
	
	
	public class EndDay extends OneShotBehaviour
	{
		public EndDay(Agent a)
		{
			super(a);
		}
		
		@Override
		public void action()
		{
			ACLMessage msgEndDay = new ACLMessage(ACLMessage.INFORM);
			msgEndDay.addReceiver(tickerAgent);
			msgEndDay.setContent("done");
			myAgent.send(msgEndDay);
						
			//toBuy1.clear();
			//toBuy2.clear();
			
			componentCost = 0;
			orderPayment = 0;
			ordersSent = 0;

			
			System.out.println("workingOrders " + workingOrders);
			System.out.println("ordersToAssembly " + ordersToAssembly);
			System.out.println("workingOrdersToBuyScreBatt " + workingOrdersToBuyScreBatt);
			System.out.println("lateOrders " + lateOrders);

			
			//Send messages to all suppliersAgent and customersAgent
			ACLMessage customerDone = new ACLMessage(ACLMessage.INFORM);
			customerDone.setContent("done");
			for(AID customer : customersAgent)
			{
				customerDone.addReceiver(customer);
			}
			myAgent.send(customerDone);
			
			ACLMessage supplierDone = new ACLMessage(ACLMessage.INFORM);
			supplierDone.setContent("done");
			for(AID supplier : suppliersAgent)
			{
				supplierDone.addReceiver(supplier);
			}
			myAgent.send(supplierDone);
			
			day++;
		}
	}
}