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


public class ___ManufacturerAgent extends Agent{

	private Codec codec = new SLCodec();
	private Ontology ontology = SmartphoneOntology.getInstance();
	
	private AID tickerAgent;
	private ArrayList<AID> customerAgents = new ArrayList<>();
	private ArrayList<AID> supplierAgents = new ArrayList<>();
	private ArrayList<Order> workingOrders = new ArrayList<>();
	private ArrayList<Order> lateToDeliver = new ArrayList<>();
	private HashMap<Integer, Integer> stockInWarehouse = new HashMap<>(); 
	private HashMap<Item, Integer> compToOrderFromSupplier = new HashMap<>();
	private ArrayList<Sell> workingDeliveries = new ArrayList<>();
	private Order tempOrder = new Order();
	private int day = 0;

	
	private int storageCost = 5;
	private int dailySpend = 0;
	private int orderPayment = 0;
	private int ordersSent = 0;
	private int profit;
	
	___Comparator compare = new ___Comparator();
	
	@Override
	protected void setup() {	
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		
		//add agent to yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Manufacturer");
		sd.setName(getLocalName() + "-Manufacturer-agent");
		dfd.addServices(sd);
		try
		{
			DFService.register(this, dfd);
		}
		catch (FIPAException e)
		{
			e.printStackTrace();
		}
		//Setting Current Stock in Manufacture WareHouse


		addBehaviour(new TickerDayWaiter(this));
	}
	@Override
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Manufacturer-Agent " + getAID().getName() + " terminating.");
		try {
			DFService.deregister(this);
		} catch (FIPAException e) {
			e.printStackTrace();
		}
	}	
	
	public class TickerDayWaiter extends CyclicBehaviour {
		public TickerDayWaiter(Agent a) {
			super(a);
			//doWait(1000);
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("New Day"),
					MessageTemplate.MatchContent("terminate"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				if (tickerAgent == null) {
					tickerAgent = msg.getSender();
				}
				if (msg.getContent().equals("New Day")) {
					day++;
					System.out.println(msg.getSender() + "\nDay " + day + "| ");

					SequentialBehaviour dailyActivity = new SequentialBehaviour();
					dailyActivity.addSubBehaviour(new FindSuppliersAndCustomer(myAgent));
					//dailyActivity.addSubBehaviour(new RequestPricesFromSupplier());
					dailyActivity.addSubBehaviour(new ReceiveCustomerOrder(myAgent));
					dailyActivity.addSubBehaviour(new RequestComponentsFromSupplier());
					System.out.println("Working orders after RequestComponentsFromSupplier: "+workingOrders.size());
					dailyActivity.addSubBehaviour(new BuyComponentsFromSupplier());
					System.out.println("Working orders after BuyComponentsFromSupplier: "+workingOrders.size());
					dailyActivity.addSubBehaviour(new GetComponentsFromSupplier());
					System.out.println("Working orders after GetComponentsFromSupplier: "+workingOrders.size());
					dailyActivity.addSubBehaviour(new GetDeliveries());//Could be avoid it and added it to GetComponentsFromSupplier
					System.out.println("Working orders after GetDeliveries: "+workingOrders.size());
					
					dailyActivity.addSubBehaviour(new CompleteOrder());
					dailyActivity.addSubBehaviour(new PaymentListener());//dailyActivity.addSubBehaviour(new sendToCust(myAgent));
					dailyActivity.addSubBehaviour(new ProfitCalculator());
					dailyActivity.addSubBehaviour(new EndDay(myAgent));
					myAgent.addBehaviour(dailyActivity);

				} else {
					// termination message to end simulation
					myAgent.doDelete();
				}
			} else {
				block();
			}
		}		
	}
	

	public class FindSuppliersAndCustomer extends OneShotBehaviour {
		public FindSuppliersAndCustomer(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			DFAgentDescription supplierTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Supplier");
			supplierTemplate.addServices(sd);
			
			DFAgentDescription customerTemplate = new DFAgentDescription();
			ServiceDescription cd = new ServiceDescription();
			cd.setType("Customer");
			customerTemplate.addServices(cd);
			//System.out.print("Hey");
			try {
				customerAgents.clear();
				DFAgentDescription[] customerType = DFService.search(myAgent, customerTemplate);
				for (int i = 0; i < customerType.length; i++) {
					customerAgents.add(customerType[i].getName());
				}
				//System.out.println("Number Customer Agents: " + customerAgents.size());
				
				supplierAgents.clear();
				DFAgentDescription[] supplierType = DFService.search(myAgent, supplierTemplate);
				for (int i = 0; i < supplierType.length; i++) {
					supplierAgents.add(supplierType[i].getName());
				}
				//System.out.println("Number Supplier Agents: Lenght " + supplierType.length);
				//System.out.println("Number Supplier Agents: " + supplierAgents.size());

			} catch (FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	/*public class RequestPricesFromSupplier extends Behaviour {
		@Override
		
		public void action() 
		{
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null)
			{
				try
				{
					ContentElement ce  = null;
					
					ce = getContentManager().extractContent(msg);
					if (ce instanceof SupplierComponent)
					{
						SupplierComponent sups = (SupplierComponent) ce;
						Item supplier = sups.getItem();
						int price = sups.getPrice();
						stockSupplier.put(supplier, price);				
					}
				}
				catch (CodecException ce) {
					ce.printStackTrace();
				}
				catch (OntologyException oe) {
					oe.printStackTrace();
				}
			}
			else
			{
					block();
			}	

		}

		@Override
		public boolean done() {
			// TODO Auto-generated method stub
			return false;
		}
		
	}*/
	
	public class ReceiveCustomerOrder extends Behaviour {
		
		private ArrayList<Order> ordersToStudy = new ArrayList<>();
		private Order bestOrder;
		private int orderCounter = 0;
		public ReceiveCustomerOrder(Agent a) {
			super(a);
		}

		@Override
		public void action() 
		{
			System.out.println("Inside ReceiveCustomerOrder");
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage order = myAgent.receive(mt);
			if(order != null)
			{
				orderCounter++;
				System.out.println("Counter " + orderCounter);
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
							ordersToStudy.add(custOrder);
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
			if(orderCounter == customerAgents.size())
			{
				System.out.println("Inside if(orderCounter == customerAgents.size()) ");
				workingOrders.add(bestOrder);
				tempOrder = bestOrder;
				Screen screen = bestOrder.getSmartphone().getScreen();
				Memory ram = bestOrder.getSmartphone().getMemory();
				Storage storage = bestOrder.getSmartphone().getStorage();
				Battery battery = bestOrder.getSmartphone().getBattery();
				int quantity = bestOrder.getQuantity();
				
				compToOrderFromSupplier.put(screen, 25);
				
				if(compToOrderFromSupplier.containsKey(screen))
				{
					compToOrderFromSupplier.put(screen, (compToOrderFromSupplier.get(screen) + quantity));
				}
				else
				{
					compToOrderFromSupplier.put(screen, quantity);
				}
				if(compToOrderFromSupplier.containsKey(ram))
				{
					compToOrderFromSupplier.put(ram, (compToOrderFromSupplier.get(ram) + quantity));
				}
				else
				{
					compToOrderFromSupplier.put(ram, quantity);
				}
				if(compToOrderFromSupplier.containsKey(storage))
				{
					compToOrderFromSupplier.put(storage, (compToOrderFromSupplier.get(storage) + quantity));
				}
				else
				{
					compToOrderFromSupplier.put(storage, quantity);
				}
				if(compToOrderFromSupplier.containsKey(battery))
				{
					compToOrderFromSupplier.put(battery, (compToOrderFromSupplier.get(battery) + quantity));
				}
				else
				{
					compToOrderFromSupplier.put(battery, quantity);
				}
				
				
				System.out.println("Manufacturer has accepted an order from " + bestOrder.getPurchaser().getName() + " due for day " + bestOrder.getDueDate());
				System.out.print("");
				
				for (int i = 0; i < ordersToStudy.size(); i++)
				{
					if(ordersToStudy.get(i) == bestOrder)
					{
						ACLMessage reply = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
						reply.addReceiver(ordersToStudy.get(i).getPurchaser());
						reply.setConversationId("order-reply");
						reply.setLanguage(codec.getName());
						reply.setOntology(ontology.getName());
						
						Order ord = ordersToStudy.get(i);
						
						Action sendReply = new Action();
						sendReply.setAction(ord);
						sendReply.setActor(ordersToStudy.get(i).getPurchaser());
						
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
						reply.addReceiver(ordersToStudy.get(i).getPurchaser());
						reply.setConversationId("order-reply");
						reply.setLanguage(codec.getName());
						reply.setOntology(ontology.getName());
						
						Order ord = ordersToStudy.get(i);
						
						Action sendReply = new Action();
						sendReply.setAction(ord);
						sendReply.setActor(ordersToStudy.get(i).getPurchaser());
						
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
			return orderCounter == customerAgents.size();
		}
		
	}
	
	public class RequestComponentsFromSupplier extends Behaviour
	{
		int sent = 0;
		@Override
		public void action(){
			//doWait(5000);
			for(Item key : compToOrderFromSupplier.keySet()){
				ACLMessage msg = new ACLMessage(ACLMessage.QUERY_IF);
				msg.setLanguage(codec.getName());
				msg.setOntology(ontology.getName());
				
				Buy buy = new Buy();
				buy.setItem(key);
				//System.out.println("Number Supplier Agents: " + supplierAgents.size());
				//System.out.println("After Buy.setItem(key)");
				
				for(int i = 0; i < supplierAgents.size(); i++){
					msg.addReceiver(supplierAgents.get(i));
					buy.setOwner(supplierAgents.get(i));
				}try {
					getContentManager().fillContent(msg, buy);
					send(msg);
					sent++;
				}catch (CodecException ce) {
					ce.printStackTrace();
				}catch (OntologyException oe) {
					oe.printStackTrace();
				} 
			}
		}

		@Override
		public boolean done() 
		{
			return sent == compToOrderFromSupplier.size();
		}
		
	}
	
	public class BuyComponentsFromSupplier extends Behaviour
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
							Buy buy = (Buy) ce;
							
							//if the component is a screen or a battery supplier 1 will be supplier
							if(buy.getItem().getItemID() <=2 || buy.getItem().getItemID() >=5)
							{
								ACLMessage buying = new ACLMessage(ACLMessage.PROPOSE);
								buying.addReceiver(buy.getOwner());
								buying.setLanguage(codec.getName());
								buying.setOntology(ontology.getName());
								
								Sell sell = new Sell();
								sell.setBuyer(getAID());
								sell.setItem(buy.getItem());
								sell.setQuantity(tempOrder.getQuantity());
								
								Action myOrder = new Action();
								myOrder.setAction(sell);
								myOrder.setActor(buy.getOwner());
								

								getContentManager().fillContent(buying, myOrder);
								send(buying);

	
							}
							//if the due date is in 4 or more days use supplier 2
							else if(buy.getShipmentSpeed() == 4 && ((tempOrder.getDueDate() - day) >= 4))
							{
								ACLMessage buying = new ACLMessage(ACLMessage.PROPOSE);
								buying.addReceiver(buy.getOwner());
								buying.setLanguage(codec.getName());
								buying.setOntology(ontology.getName());
								
								Sell sell = new Sell();
								sell.setBuyer(getAID());
								sell.setItem(buy.getItem());
								sell.setQuantity(tempOrder.getQuantity());
								
								Action myOrder = new Action();
								myOrder.setAction(sell);
								myOrder.setActor(buy.getOwner());
								

								getContentManager().fillContent(buying, myOrder);
								send(buying);

							}
							//if the due date is in less than 4 days use supplier 1
							else if(buy.getShipmentSpeed() == 1 && ((tempOrder.getDueDate() - day) < 4))
							{
								ACLMessage buying = new ACLMessage(ACLMessage.PROPOSE);
								buying.addReceiver(buy.getOwner());
								buying.setLanguage(codec.getName());
								buying.setOntology(ontology.getName());
								
								Sell sell = new Sell();
								sell.setBuyer(getAID());
								sell.setItem(buy.getItem());
								sell.setQuantity(tempOrder.getQuantity());
								
								Action myOrder = new Action();
								myOrder.setAction(sell);
								myOrder.setActor(buy.getOwner());
								
							
								getContentManager().fillContent(buying, myOrder);
								send(buying);
								
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
			return noReplies == (compToOrderFromSupplier.size() * 2);		
		}
	}
	
	public class GetComponentsFromSupplier extends Behaviour
	{
		int noReplies = 0;
		public void action()
		{
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchPerformative(ACLMessage.FAILURE));
			ACLMessage msg = myAgent.receive(mt);
			
			if(msg != null){
				noReplies++;
				if(msg.getPerformative() == ACLMessage.INFORM){
					try	{
						ContentElement ce = null;
						
						ce = getContentManager().extractContent(msg);
						if(ce instanceof Action){
							Concept action = ((Action)ce).getAction();
							if(action instanceof Sell){
								Sell order = (Sell)action;
								
								workingDeliveries.add(order);
								dailySpend = dailySpend + order.getPrice();
								
								System.out.println("Components purchased! " + tempOrder.getQuantity() + " x " + order.getItem().getClass().getName() + " purchased from " + msg.getSender().getName());
								System.out.println("Cost: " + order.getPrice());
							}
						}	
					}catch (CodecException ce){
						ce.printStackTrace();
					}
					catch (OntologyException oe){
						oe.printStackTrace();
					}
				}
			}
			else{
				block();
			}
			if(noReplies == compToOrderFromSupplier.size()){
				System.out.println("");
			}
		}
		
		public boolean done(){
			return noReplies == compToOrderFromSupplier.size();
		}
	}
	
	public class GetDeliveries extends OneShotBehaviour{
		public void action() 
		{
			System.out.println("Arrives To GetDeliveries on ManufacturAgent.");
			
			
			if(!workingDeliveries.isEmpty())
			{
				for(Sell order : workingDeliveries)
				{
					if(order.getDeliveryDate() == day)
					{//This could fails
						if(stockInWarehouse.containsKey(order.getItem().getItemID()))
						{
							int quantity = stockInWarehouse.get(order.getItem().getItemID());
							stockInWarehouse.put(order.getItem().getItemID(), quantity + order.getQuantity());
							System.out.println("Order of " + order.getQuantity() + " x " + order.getItem() + " added to Stocklist");
						}
						else
						{
							stockInWarehouse.put(order.getItem().getItemID(), order.getQuantity());
							System.out.println("Order of " + order.getQuantity() + " x " + order.getItem() + " added to Stocklist");
						}
					}
				}
			}

			System.out.println("");
		}
		
	}
	public class CompleteOrder extends OneShotBehaviour
	{
		int dailyLimit = 50;
		public void action() 
		{
			System.out.println("InsideCompleteOrder");
			if(workingOrders.isEmpty() == false)
			{
				System.out.println("Inside workingOrders.isEmpty()");
				for(int i = 0; i < workingOrders.size(); i++)
				{
					Order order = workingOrders.get(i);
					if(order.getDueDate() < day)
					{
						lateToDeliver.add(order);
						workingOrders.remove(order);
					}
				}
			}
			try
			{
				if(lateToDeliver.isEmpty() == false)
				{
					System.out.println("Inside lateToDeliver.isEmpty() == false");
					for(int i = 0; i < lateToDeliver.size(); i++)
					{
						Order order = lateToDeliver.get(i);
						int screen = order.getSmartphone().getScreen().getItemID(); 
						int battery = order.getSmartphone().getBattery().getItemID();
						int ram = order.getSmartphone().getMemory().getItemID();
						int storage = order.getSmartphone().getStorage().getItemID();
						
						if(order.getQuantity() < dailyLimit)
						{
							System.out.println("Inside (order.getQuantity() < dailyLimit)");
							if(stockInWarehouse.containsKey(screen) && stockInWarehouse.containsKey(storage) && stockInWarehouse.containsKey(ram) && stockInWarehouse.containsKey(storage))
							{ //Probably is going to fail!!!!
								System.out.println("Inside stockInWarehouse.containsKey(screen)");
								if(stockInWarehouse.get(screen) >= order.getQuantity() && stockInWarehouse.get(battery) >= order.getQuantity()
										&& stockInWarehouse.get(ram) >= order.getQuantity() && stockInWarehouse.get(storage) >= order.getQuantity())
								{
									//assemble phone order
									dailyLimit -= order.getQuantity();
									stockInWarehouse.put(order.getSmartphone().getBattery().getItemID(), (stockInWarehouse.get(screen) - order.getQuantity())); //brake for sure
									stockInWarehouse.put(battery, (stockInWarehouse.get(battery) - order.getQuantity()));
									stockInWarehouse.put(ram, (stockInWarehouse.get(ram) - order.getQuantity()));
									stockInWarehouse.put(storage, (stockInWarehouse.get(storage) - order.getQuantity()));

									ACLMessage msg = new ACLMessage(ACLMessage.CONFIRM);
									msg.addReceiver(order.getPurchaser());
									msg.setLanguage(codec.getName());
									msg.setOntology(ontology.getName());
									
									Deliver deliver = new Deliver();
									deliver.setOrder(order);
									
									Action myDelivery = new Action();
									myDelivery.setAction(deliver);
									myDelivery.setActor(getAID());
									
									getContentManager().fillContent(msg, myDelivery);
									send(msg);
									System.out.println("OrderSent Increase");
									ordersSent++;
									lateToDeliver.remove(order);
									System.out.println("Order for " + order.getQuantity() + " x " + order.getSmartphone().getName() + "s sent to " + order.getPurchaser().getName());

								}
							}
						}
					}
				}
				
				else if(workingOrders.isEmpty() == false)
				{
					for(int i = 0; i < workingOrders.size(); i++)
					{
						Order order = workingOrders.get(i);
						Screen screen = order.getSmartphone().getScreen();
						Battery battery = order.getSmartphone().getBattery();
						Memory ram = order.getSmartphone().getMemory();
						Storage storage = order.getSmartphone().getStorage();
						
						if(order.getQuantity() < dailyLimit) //IT shouldn't work
						{ 
							if(stockInWarehouse.containsKey(screen.getItemID()) && stockInWarehouse.containsKey(storage.getItemID()) && stockInWarehouse.containsKey(ram.getItemID()) && stockInWarehouse.containsKey(storage.getItemID()))
							{
								if(stockInWarehouse.get(screen.getItemID()) >= order.getQuantity() && stockInWarehouse.get(battery.getItemID()) >= order.getQuantity()
										&& stockInWarehouse.get(ram.getItemID()) >= order.getQuantity() && stockInWarehouse.get(storage.getItemID()) >= order.getQuantity())
								{
									//assemble phone order
									dailyLimit -= order.getQuantity();
									stockInWarehouse.put(screen.getItemID(), (stockInWarehouse.get(screen.getItemID()) - order.getQuantity()));
									stockInWarehouse.put(battery.getItemID(), (stockInWarehouse.get(battery.getItemID()) - order.getQuantity()));
									stockInWarehouse.put(ram.getItemID(), (stockInWarehouse.get(ram.getItemID()) - order.getQuantity()));
									stockInWarehouse.put(storage.getItemID(), (stockInWarehouse.get(storage.getItemID()) - order.getQuantity()));

									ACLMessage msg = new ACLMessage(ACLMessage.CONFIRM);
									msg.addReceiver(order.getPurchaser());
									msg.setLanguage(codec.getName());
									msg.setOntology(ontology.getName());

									Deliver deliver = new Deliver();
									deliver.setOrder(order);
									
									Action myDelivery = new Action();
									myDelivery.setAction(deliver);
									myDelivery.setActor(getAID());
									
									getContentManager().fillContent(msg, myDelivery);
									send(msg);
									
									ordersSent++;
									workingOrders.remove(order);
									System.out.println("Order for " + order.getQuantity() + " x " + order.getSmartphone().getName() + "s sent to " + order.getPurchaser().getName());

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
			System.out.println("Inside PaymentListener");
			System.out.println("Got payment. Message Recieved"+ msgReceived + "Order Sent" + ordersSent);
			if(order!=null)
			{
				msgReceived++;
				System.out.println("Got payment. Message Recieved "+ msgReceived + "Order Sent " + ordersSent);
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
			
			if(lateToDeliver.isEmpty())
			{
				System.out.println("Total cost of late fees: " + lateFees);
			}
			else
			{
				for(Order order : lateToDeliver)
				{
					lateFees += order.getDelayFee();
				}
				System.out.println("Total cost of late fees: " + lateFees);
			} 
			
			if(stockInWarehouse.isEmpty())
			{
				System.out.println("Total cost of warehouse storage: " + warehouseCost);
			}
			else
			{
				for(Integer v : stockInWarehouse.values())
				{
					warehouseCost += (v * storageCost);
				}
				System.out.println("Total cost of warehouse storage: " + warehouseCost);
			}
			
			System.out.println("Total cost of supplies purchased: " + dailySpend);
			
			dailyProfit = orderPayment - lateFees - warehouseCost - dailySpend;
			profit += dailyProfit;
			
			System.out.println("Todays profit: " + dailyProfit);
			System.out.println("Total profit: " + profit);
			
		}
		
	}
	
	public class EndDay extends OneShotBehaviour {

		public EndDay(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			System.out.println("Reach EndDay At Manufacture");
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(tickerAgent);
			msg.setContent("done");
			myAgent.send(msg);
			
			
			dailySpend = 0;
			orderPayment = 0;
			ordersSent = 0;
			// send a message to each supplier that we have finished
			ACLMessage supDone = new ACLMessage(ACLMessage.INFORM);
			supDone.setContent("done");
			System.out.println("SuookuerAgents: " + supplierAgents.size());
			for (AID supplier : supplierAgents) {
				System.out.println("Suppliers: " + supplier.getLocalName() + ".Messsage: " + supDone.getContent());
				supDone.addReceiver(supplier);
			}
			myAgent.send(supDone);
			
			ACLMessage custDone = new ACLMessage(ACLMessage.INFORM);
			custDone.setContent("done");
			for (AID customer : customerAgents) {
				custDone.addReceiver(customer);
			}
			myAgent.send(custDone);
			
			day++;
		}
	}
}
