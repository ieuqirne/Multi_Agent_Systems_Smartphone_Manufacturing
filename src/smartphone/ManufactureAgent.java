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
	
	private ArrayList<AID> customers = new ArrayList<>();
	private ArrayList<AID> suppliers = new ArrayList<>();
	private ArrayList<Order> openOrders = new ArrayList<>();//workingOrders
	private ArrayList<Order> lateOrders = new ArrayList<>();
	private ArrayList<Sell> openDeliveries = new ArrayList<>();
	private HashMap<Item, Integer> toBuy = new HashMap<>();//compToOrderFromSupplier
	private HashMap<Integer, Integer> stockList = new HashMap<>();//StockInWarehouse
	private Order currentOrder = new Order();
	private AID tickerAgent;
	private int day = 0;
	private int ordersSent = 0;
	
	private int warehouseStorageCost = 5;
	private int componentCost = 0; // 
	private int orderPayment = 0;
	private int totalProfit;
	


	@Override 
	protected void setup()
	{
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		
		//add agent to yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("manufacturer");
		sd.setName(getLocalName() + "-manufacturer-agent");
		dfd.addServices(sd);
		try
		{
			DFService.register(this, dfd);
		}
		catch (FIPAException e)
		{
			e.printStackTrace();
		}
		
		addBehaviour(new TickerWaiter(this));
	}
	
	protected void takedown()
	{
		//Deregister from yellow pages
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
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("new day"),
					MessageTemplate.MatchContent("terminate"));
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null)
			{
				if(tickerAgent == null)
				{
					tickerAgent = msg.getSender();
				}
				if(msg.getContent().equals("new day"))
				{
					//day++;
					/*
					 * Add customer behaviours here
					 */
					System.out.println(msg.getSender() + "\nDay " + day + "| ");
					//spawn new sequential for day's activity
					SequentialBehaviour dailyActivity = new SequentialBehaviour();
					//sub-behaviours will execute in the order they are added
					dailyActivity.addSubBehaviour(new FindAgents(myAgent));
					dailyActivity.addSubBehaviour(new AcceptOrder(myAgent));
					dailyActivity.addSubBehaviour(new QueryComponents());
					dailyActivity.addSubBehaviour(new BuyComponents());
					dailyActivity.addSubBehaviour(new ComponentOrderListener());
					dailyActivity.addSubBehaviour(new GetDeliveries());
					dailyActivity.addSubBehaviour(new CompleteOrder());
					dailyActivity.addSubBehaviour(new PaymentListener());
					dailyActivity.addSubBehaviour(new ProfitCalculator());
					dailyActivity.addSubBehaviour(new EndDay(myAgent));
					
					myAgent.addBehaviour(dailyActivity);
				}
				else
				{
					//termination message to end simulation
					myAgent.doDelete();
				}
			}
			else
			{
				block();
			}
		}
	}	
	
	
	public class FindAgents extends OneShotBehaviour
	{
		public FindAgents(Agent a)
		{
			super(a);
		}
		
		@Override 
		public void action()
		{
			DFAgentDescription customerTemplate = new DFAgentDescription();
			ServiceDescription csd = new ServiceDescription();
			csd.setType("customer");
			customerTemplate.addServices(csd);
			
			DFAgentDescription supplierTemplate = new DFAgentDescription();
			ServiceDescription ssd = new ServiceDescription();
			ssd.setType("supplier");
			supplierTemplate.addServices(ssd);
			
			try
			{
				customers.clear();
				DFAgentDescription[] custAgent = DFService.search(myAgent, customerTemplate);
				for(int i = 0; i<custAgent.length; i++)
				{
					customers.add(custAgent[i].getName());
				}
				System.out.println("Number Supplier Agents: " + customers.size());
				suppliers.clear();
				DFAgentDescription[] supplierAgent = DFService.search(myAgent, supplierTemplate);
				for(int i = 0; i<supplierAgent.length; i++)
				{
					suppliers.add(supplierAgent[i].getName());
				}
				System.out.println("Number Supplier Agents: " + suppliers.size());
			}
			catch (FIPAException fe)
			{
				fe.printStackTrace();
			}
		}
	}
	
	/*
	 * The AcceptOrder behaviour receives all order proposals from the customerAgents
	 * it decides which to accept (based on highest price) and adds that order to the 
	 * openOrder list. each customer is either sent a REFUSE or ACCEPT_PROPOSAL reply.
	 */
	public class AcceptOrder extends Behaviour
	{

		private int numOrders;
		private Order bestOrder;
		private ArrayList<Order> customersOrders = new ArrayList<>();
		
		public AcceptOrder(Agent a)
		{
			super(a);
		}
		
		@Override
		public void action() 
		{
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
			if(numOrders == customers.size())
			{
				openOrders.add(bestOrder);
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
						reply.addReceiver(customersOrders.get(i).getPurchaser());
						reply.setConversationId("order-reply");
						reply.setLanguage(codec.getName());
						reply.setOntology(ontology.getName());
						
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
						reply.addReceiver(customersOrders.get(i).getPurchaser());
						reply.setConversationId("order-reply");
						reply.setLanguage(codec.getName());
						reply.setOntology(ontology.getName());
						
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
			return numOrders == customers.size();
		}
		
	}
	
	
	public class QueryComponents extends Behaviour
	{
		int sent = 0;
		@Override
		public void action() 
		{
			
			
			for(Item key : toBuy.keySet())
			{
				ACLMessage msg = new ACLMessage(ACLMessage.QUERY_IF);
				msg.setLanguage(codec.getName());
				msg.setOntology(ontology.getName());
				
				Buy owns = new Buy();
				owns.setItem(key);
				
				for(int i = 0; i < suppliers.size(); i++)
				{
					msg.addReceiver(suppliers.get(i));
					owns.setOwner(suppliers.get(i));
				}
				try 
				{
					// Let JADE convert from Java objects to string
					getContentManager().fillContent(msg, owns);
					send(msg);
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
	
	
	public class BuyComponents extends Behaviour
	{
		int noReplies = 0;
		
		public void action()
		{
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.CFP),
					MessageTemplate.MatchPerformative(ACLMessage.REFUSE));
			ACLMessage msg = myAgent.receive(mt);
			if(msg!=null)
			{
				noReplies++;
				if(msg.getPerformative() == ACLMessage.CFP)
				{


					try
					{
						ContentElement ce = null;

						ce = getContentManager().extractContent(msg);
						if(ce instanceof Buy)
						{
							Buy owns = (Buy) ce;
							
							//if the component is a screen or a battery supplier 1 will be supplier
							if(owns.getItem().getItemID() == 1 || owns.getItem().getItemID() == 2 || owns.getItem().getItemID() == 7 || owns.getItem().getItemID() == 8)
							{
								ACLMessage buy = new ACLMessage(ACLMessage.PROPOSE);
								buy.addReceiver(owns.getOwner());
								buy.setLanguage(codec.getName());
								buy.setOntology(ontology.getName());
								
								Sell sell = new Sell();
								sell.setBuyer(getAID());
								sell.setItem(owns.getItem());
								sell.setQuantity(currentOrder.getQuantity());
								
								Action myOrder = new Action();
								myOrder.setAction(sell);
								myOrder.setActor(owns.getOwner());
								

								getContentManager().fillContent(buy, myOrder);
								send(buy);

	
							}
							//if the due date is in 4 or more days use supplier 2
							else if(owns.getShipmentSpeed() == 4 && ((currentOrder.getDueDate() - day) >= 4))
							{
								ACLMessage buy = new ACLMessage(ACLMessage.PROPOSE);
								buy.addReceiver(owns.getOwner());
								buy.setLanguage(codec.getName());
								buy.setOntology(ontology.getName());
								
								Sell sell = new Sell();
								sell.setBuyer(getAID());
								sell.setItem(owns.getItem());
								sell.setQuantity(currentOrder.getQuantity());
								
								Action myOrder = new Action();
								myOrder.setAction(sell);
								myOrder.setActor(owns.getOwner());
								

								getContentManager().fillContent(buy, myOrder);
								send(buy);

							}
							//if the due date is in less than 4 days use supplier 1
							else if(owns.getShipmentSpeed() == 1 && ((currentOrder.getDueDate() - day) < 4))
							{
								ACLMessage buy = new ACLMessage(ACLMessage.PROPOSE);
								buy.addReceiver(owns.getOwner());
								buy.setLanguage(codec.getName());
								buy.setOntology(ontology.getName());
								
								Sell sell = new Sell();
								sell.setBuyer(getAID());
								sell.setItem(owns.getItem());
								sell.setQuantity(currentOrder.getQuantity());
								
								Action myOrder = new Action();
								myOrder.setAction(sell);
								myOrder.setActor(owns.getOwner());
								
							
								getContentManager().fillContent(buy, myOrder);
								send(buy);
								
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
	
	
	public class ComponentOrderListener extends Behaviour
	{
		int noReplies = 0;
		public void action()
		{
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchPerformative(ACLMessage.FAILURE));
			ACLMessage msg = myAgent.receive(mt);
			
			if(msg != null)
			{
				noReplies++;
				if(msg.getPerformative() == ACLMessage.INFORM)
				{
					try
					{
						ContentElement ce = null;
						
						ce = getContentManager().extractContent(msg);
						if(ce instanceof Action)
						{
							Concept action = ((Action)ce).getAction();
							if(action instanceof Sell)
							{
								Sell order = (Sell)action;
								
								openDeliveries.add(order);
								componentCost += order.getPrice();
								
								System.out.println("Components purchased! " + currentOrder.getQuantity() + " x " + order.getItem().getClass().getName() + " purchased from " + msg.getSender().getName());
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
	
	
	public class GetDeliveries extends OneShotBehaviour
	{

		public void action() 
		{
			if(!openDeliveries.isEmpty())
			{
				for(Sell order : openDeliveries)
				{
					if(order.getDeliveryDate() == day)
					{
						if(stockList.containsKey(order.getItem().getItemID()))
						{
							int quantity = stockList.get(order.getItem().getItemID());
							stockList.put(order.getItem().getItemID(), quantity + order.getQuantity());
							System.out.println("Order of " + order.getQuantity() + " x " + order.getItem() + " added to Stocklist");
						}
						else
						{
							stockList.put(order.getItem().getItemID(), order.getQuantity());
							System.out.println("Order of " + order.getQuantity() + " x " + order.getItem() + " added to Stocklist");
						}
					}
				}
			}
			//System.out.println(stockList.keySet());
			//System.out.println(stockList.values());
			System.out.println("");
		}
		
	}
	
	
	public class CompleteOrder extends OneShotBehaviour
	{
		int dailyLimit = 50;
		public void action() 
		{
			if(openOrders.isEmpty() == false)
			{
				for(int i = 0; i < openOrders.size(); i++)
				{
					Order o = openOrders.get(i);
					if(o.getDueDate() < day)
					{
						lateOrders.add(o);
						openOrders.remove(o);
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
						
						if(o.getQuantity() < dailyLimit)
						{
							if(stockList.containsKey(screen) && stockList.containsKey(storage) && stockList.containsKey(ram) && stockList.containsKey(storage))
							{
								System.out.println("Stocklist: " + stockList);
								System.out.println("Stocklist Screen: " + stockList.get(screen));
								System.out.println("Quantity Order: " + o.getQuantity());
								System.out.println(" battery id: " + battery);
								System.out.println("Stocklist battery: " + stockList.get(battery));
								System.out.println("Stocklist ram: " + stockList.get(ram));
								System.out.println("Stocklist storage: " + stockList.get(storage));
		
								
								if(stockList.get(screen) >= o.getQuantity() && stockList.get(battery) >= o.getQuantity()
										&& stockList.get(ram) >= o.getQuantity() && stockList.get(storage) >= o.getQuantity())
								{
									//assemble phone order
									dailyLimit -= o.getQuantity();
									stockList.put(screen, (stockList.get(screen) - o.getQuantity()));
									stockList.put(battery, (stockList.get(battery) - o.getQuantity()));
									stockList.put(ram, (stockList.get(ram) - o.getQuantity()));
									stockList.put(storage, (stockList.get(storage) - o.getQuantity()));

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
									lateOrders.remove(o);
									System.out.println("Order for " + o.getQuantity() + " x " + o.getSmartphone().getName() + "s sent to " + o.getPurchaser().getName());

								}
							}
						}
					}
				}
				
				else if(openOrders.isEmpty() == false)
				{
					for(int i = 0; i < openOrders.size(); i++)
					{
						Order o = openOrders.get(i);
						int screen = o.getSmartphone().getScreen().getItemID();
						int battery = o.getSmartphone().getBattery().getItemID();
						int ram = o.getSmartphone().getMemory().getItemID();
						int storage = o.getSmartphone().getStorage().getItemID();
						
						if(o.getQuantity() < dailyLimit)
						{
							if(stockList.containsKey(screen) && stockList.containsKey(storage) && stockList.containsKey(ram) && stockList.containsKey(storage))
							{
								System.out.println("Stocklist: " + stockList);
								System.out.println("Stocklist Screen: " + stockList.get(screen));
								System.out.println("Quantity Order: " + o.getQuantity());
								System.out.println(" batteryID: " + battery);
								System.out.println("Stocklist battery: " + stockList.get(battery));
								System.out.println("Stocklist ram: " + stockList.get(ram));
								System.out.println("Stocklist storage: " + stockList.get(storage));
		
								
								if(stockList.get(screen) >= o.getQuantity() && stockList.get(battery) >= o.getQuantity()
										&& stockList.get(ram) >= o.getQuantity() && stockList.get(storage) >= o.getQuantity())
								{
									//assemble phone order
									dailyLimit -= o.getQuantity();
									stockList.put(screen, (stockList.get(screen) - o.getQuantity()));
									stockList.put(battery, (stockList.get(battery) - o.getQuantity()));
									stockList.put(ram, (stockList.get(ram) - o.getQuantity()));
									stockList.put(storage, (stockList.get(storage) - o.getQuantity()));

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
									openOrders.remove(o);
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
	
	
	public class PaymentListener extends Behaviour
	{
		int msgReceived = 0;
		
		public void action() 
		{
			MessageTemplate mt = MessageTemplate.MatchConversationId("Order payment");
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
	
	
	public class ProfitCalculator extends OneShotBehaviour
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
			
			if(stockList.isEmpty())
			{
				System.out.println("Total cost of warehouse storage: " + warehouseCost);
			}
			else
			{
				for(Integer v : stockList.values())
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
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(tickerAgent);
			msg.setContent("done");
			myAgent.send(msg);
			
			System.out.println("");
			
			toBuy.clear();
			componentCost = 0;
			orderPayment = 0;
			ordersSent = 0;

			
			
			//Send messages to all suppliers and customers
			ACLMessage customerDone = new ACLMessage(ACLMessage.INFORM);
			customerDone.setContent("done");
			for(AID customer : customers)
			{
				customerDone.addReceiver(customer);
			}
			myAgent.send(customerDone);
			
			ACLMessage supplierDone = new ACLMessage(ACLMessage.INFORM);
			supplierDone.setContent("done");
			for(AID supplier : suppliers)
			{
				supplierDone.addReceiver(supplier);
			}
			myAgent.send(supplierDone);
			
			day++;
		}
	}
}