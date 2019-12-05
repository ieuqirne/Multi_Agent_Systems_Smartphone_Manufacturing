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
	private ArrayList<Order> workingOrders = new ArrayList<>();//workingOrders
	private ArrayList<Order> lateOrders = new ArrayList<>();
	private ArrayList<Sell> openDeliveries = new ArrayList<>();
	private HashMap<Item, Integer> toBuy = new HashMap<>();//compToOrderFromSupplier
	private HashMap<Integer, Integer> warehouseStock = new HashMap<>();//StockInWarehouse
	private Order currentOrder = new Order();
	private AID tickerAgent;
	private int day = 0;
	private int ordersSent = 0;
	private int warehouseStorageCost = 5;
	private int componentCost = 0; // 
	private int orderPayment = 0;
	private int totalProfit;
	
	/*private int [] smartphoneDayToAssembly = new int[101];
	private Screen[] screen7ToBuy;
	private ArrayList<Screen> screen5ToBuy = new ArrayList<>();*/


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
		
		/*
		for(int x = 1; x <= TickerAgent.NUM_DAYS; x++) {
			smartphoneDayToAssembly[x] = 0;
		}
		*/
		
		
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
					dailyActivity.addSubBehaviour(new BuyComponentsSuppliers());
					dailyActivity.addSubBehaviour(new GetComponentsFromSuppliers());
					dailyActivity.addSubBehaviour(new GetDeliveriesFromSuppliers());
					dailyActivity.addSubBehaviour(new AssemblySmartphones());
					dailyActivity.addSubBehaviour(new GetPaymentsFromSuppliers());
					dailyActivity.addSubBehaviour(new GetProfit());
					dailyActivity.addSubBehaviour(new EndDay(myAgent));
					
					myAgent.addBehaviour(dailyActivity);
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
		private ArrayList<Order> customersOrders = new ArrayList<>();
		
		public ReceiveCustomerOrders(Agent a)
		{
			super(a);
		}
		
		@Override
		public void action() 
		{
		/*	MessageTemplate mt2 = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
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
							customersOrders.add(custOrder);
							if(bestOrder == null)
							{
								bestOrder = custOrder;
							}
							else if((custOrder.getQuantity()) < (bestOrder.getQuantity()))
							{
								bestOrder = custOrder;
								
							}
						}
					}
					
					
				}catch (CodecException ce){
					ce.printStackTrace();
				}catch (OntologyException oe){
					oe.printStackTrace();
				}
				
			}
				
			*/
			
			
			
			
			
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage order = myAgent.receive(mt);
			if(order != null)
			{
				numOrders++;
				try
				{
					ContentElement ce = null;
					
					ce = getContentManager().extractContent(order);
					if(ce instanceof Action)
					{
						Concept action = ((Action)ce).getAction();
						if(action instanceof Order)
						{
							Order custOrder = (Order)action;
							customersOrders.add(custOrder);
							if(bestOrder == null)
							{
								bestOrder = custOrder;
							}
							else if((custOrder.getQuantity()) < (bestOrder.getQuantity()))
							{
								bestOrder = custOrder;
								
							}
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
			else 
			{
				block();
			}
			
			//Send replies to each customer
			if(numOrders == customersAgent.size())
			{
				workingOrders.add(bestOrder);
				currentOrder = bestOrder;
				Screen screen = bestOrder.getSmartphone().getScreen();
				Memory ram = bestOrder.getSmartphone().getMemory();
				Storage storage = bestOrder.getSmartphone().getStorage();
				Battery battery = bestOrder.getSmartphone().getBattery();
				int quantity = bestOrder.getQuantity();
				
				toBuy.put(screen, 25);
				
				if(toBuy.containsKey(screen))
				{
					toBuy.put(screen, (toBuy.get(screen) + quantity));
				}
				else
				{
					toBuy.put(screen, quantity);
				}
				if(toBuy.containsKey(ram))
				{
					toBuy.put(ram, (toBuy.get(ram) + quantity));
				}
				else
				{
					toBuy.put(ram, quantity);
				}
				if(toBuy.containsKey(storage))
				{
					toBuy.put(storage, (toBuy.get(storage) + quantity));
				}
				else
				{
					toBuy.put(storage, quantity);
				}
				if(toBuy.containsKey(battery))
				{
					toBuy.put(battery, (toBuy.get(battery) + quantity));
				}
				else
				{
					toBuy.put(battery, quantity);
				}
				
				
				System.out.println("Manufacturer has accepted an order from " + bestOrder.getPurchaser().getName() + " due for day " + bestOrder.getDueDate());
				System.out.print("");
				
				for (int i = 0; i < customersOrders.size(); i++)
				{
					if(customersOrders.get(i) == bestOrder)
					{
						ACLMessage reply = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
						reply.setLanguage(codec.getName());
						reply.setOntology(ontology.getName());
						reply.addReceiver(customersOrders.get(i).getPurchaser());
						reply.setConversationId("ManufactureAnswerToCustomer");

						Order ord = customersOrders.get(i);
						
						Action sendReply = new Action();
						sendReply.setAction(ord);
						sendReply.setActor(customersOrders.get(i).getPurchaser());
						
						try
						{
							getContentManager().fillContent(reply, sendReply);
							send(reply);
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
					else
					{
						ACLMessage reply = new ACLMessage(ACLMessage.REFUSE);
						reply.setLanguage(codec.getName());
						reply.setOntology(ontology.getName());
						reply.addReceiver(customersOrders.get(i).getPurchaser());
						reply.setConversationId("ManufactureAnswerToCustomer");

						Order ord = customersOrders.get(i);
						
						Action sendReply = new Action();
						sendReply.setAction(ord);
						sendReply.setActor(customersOrders.get(i).getPurchaser());
						
						try
						{
							getContentManager().fillContent(reply, sendReply);
							send(reply);
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
			}
		}

		@Override
		public boolean done() 
		{
			return numOrders == customersAgent.size();
		}
		
	}
	
	
	public class RequestComponentsSupplier extends Behaviour
	{
		int sent = 0;
		@Override
		public void action() 
		{
			
			
			for(Item key : toBuy.keySet())
			{
				ACLMessage msgRequestCompSup = new ACLMessage(ACLMessage.QUERY_IF);
				msgRequestCompSup.setLanguage(codec.getName());
				msgRequestCompSup.setOntology(ontology.getName());
				
				Buy buy = new Buy();
				buy.setItem(key);
				
				for(int i = 0; i < suppliersAgent.size(); i++)
				{
					msgRequestCompSup.addReceiver(suppliersAgent.get(i));
					buy.setOwner(suppliersAgent.get(i));
				}
				try 
				{
					// Let JADE convert from Java objects to string
					getContentManager().fillContent(msgRequestCompSup, buy);
					send(msgRequestCompSup);
					sent++;
				}
				catch (CodecException ce) {
					ce.printStackTrace();
				}
				catch (OntologyException oe) {
					oe.printStackTrace();
				} 

			}

		}

		@Override
		public boolean done() 
		{
			return sent == toBuy.size();
		}
		
	}
	
	
	public class BuyComponentsSuppliers extends Behaviour
	{
		int noReplies = 0;
		
		public void action()
		{
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.CFP),
					MessageTemplate.MatchPerformative(ACLMessage.REFUSE));
			ACLMessage msgBuyComSupp = myAgent.receive(mt);
			if(msgBuyComSupp!=null)
			{
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
			
		}

		
		public boolean done()
		{
			return noReplies == (toBuy.size() * 2);		
		}
	}
	
	
	public class GetComponentsFromSuppliers extends Behaviour
	{
		int noReplies = 0;
		public void action()
		{
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchPerformative(ACLMessage.FAILURE));
			ACLMessage msgGetCompSup = myAgent.receive(mt);
			
			if(msgGetCompSup != null)
			{
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
								
								System.out.println("Components purchased! " + currentOrder.getQuantity() + " x " + order.getItem().getClass().getName() + " purchased from " + msgGetCompSup.getSender().getName());
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
			if(noReplies == toBuy.size())
			{
				System.out.println("");
			}
		}
		
		public boolean done()
		{
			return noReplies == toBuy.size();
		}
	}
	
	
	public class GetDeliveriesFromSuppliers extends OneShotBehaviour
	{

		public void action() 
		{
			if(!openDeliveries.isEmpty())
			{
				for(Sell order : openDeliveries)
				{
					if(order.getDeliveryDate() == day)
					{
						if(warehouseStock.containsKey(order.getItem().getItemID()))
						{
							int quantity = warehouseStock.get(order.getItem().getItemID());
							warehouseStock.put(order.getItem().getItemID(), quantity + order.getQuantity());
							System.out.println("Order of " + order.getQuantity() + " x " + order.getItem() + " added to Stocklist");
						}
						else
						{
							warehouseStock.put(order.getItem().getItemID(), order.getQuantity());
							System.out.println("Order of " + order.getQuantity() + " x " + order.getItem() + " added to Stocklist");
						}
					}
				}
			}
			//System.out.println(warehouseStock.keySet());
			//System.out.println(warehouseStock.values());
			System.out.println("");
		}
		
	}
	
	
	public class AssemblySmartphones extends OneShotBehaviour
	{
		int assemblyMax = 50;
		public void action() 
		{
			if(workingOrders.isEmpty() == false)
			{
				for(int i = 0; i < workingOrders.size(); i++)
				{
					Order o = workingOrders.get(i);
					if(o.getDueDate() < day)
					{
						lateOrders.add(o);
						workingOrders.remove(o);
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
				
				else if(workingOrders.isEmpty() == false)
				{
					for(int i = 0; i < workingOrders.size(); i++)
					{
						Order o = workingOrders.get(i);
						int screen = o.getSmartphone().getScreen().getItemID();
						int battery = o.getSmartphone().getBattery().getItemID();
						int ram = o.getSmartphone().getMemory().getItemID();
						int storage = o.getSmartphone().getStorage().getItemID();
						
						if(o.getQuantity() < assemblyMax)
						{
							if(warehouseStock.containsKey(screen) && warehouseStock.containsKey(storage) && warehouseStock.containsKey(ram) && warehouseStock.containsKey(storage))
							{
								/*
								System.warehouseStockln("Stocklist: " + warehouseStock);
								System.out.println("Stocklist Screen: " + warehouseStock.get(screen));
								System.out.println("Quantity Order: " + o.getQuantity());
								System.out.println(" batteryID: " + battery);
								System.out.println("Stocklist battery: " + warehouseStock.get(battery));
								System.out.println("Stocklist ram: " + warehouseStock.get(ram));
								System.out.println("Stocklist storage: " + warehouseStock.get(storage));
		
								*/
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
									workingOrders.remove(o);
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
			}
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
			
			System.out.println("");
			
			toBuy.clear();
			componentCost = 0;
			orderPayment = 0;
			ordersSent = 0;

			
			
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