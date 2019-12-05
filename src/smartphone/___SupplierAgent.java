package smartphone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
import smartphone_ontology.elements.Item;
import smartphone_ontology.elements.Memory;
import smartphone_ontology.elements.Screen;
import smartphone_ontology.elements.Sell;
import smartphone_ontology.elements.Storage;
import smartphone_ontology.elements.SupplierComponent;
import smartphones_ontology.SmartphoneOntology;


public class ___SupplierAgent extends Agent {
	
	private Codec codec = new SLCodec();
	private Ontology ontology = SmartphoneOntology.getInstance();
	private AID manufacturerAgent;
	private AID tickerAgent;
	private HashMap<Integer, Integer> components = new HashMap<>();
	//ArrayList<Item> components = new ArrayList<Item>();
	private int deliveryTime = 0;
	private int day = 1;
	
	
	
	protected void setup() {
		
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		
		//add agents to the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Supplier");
		sd.setName(getLocalName() + "-supplier-agent");
		dfd.addServices(sd);
		try
		{
			DFService.register(this, dfd);
		}
		catch (FIPAException e)
		{
			e.printStackTrace();
		}
		
		//get manufacturer
		DFAgentDescription manufacturerTemplate = new DFAgentDescription();
		ServiceDescription manufacSD = new ServiceDescription();
		manufacSD.setType("Manufacturer");
		manufacturerTemplate.addServices(manufacSD);
		try
		{
			DFAgentDescription[] agent = DFService.search(this, manufacturerTemplate);
			for(int i = 0; i<agent.length; i++)
			{
				manufacturerAgent = agent[i].getName();
				System.out.println("Manufacture Agent: " + manufacturerAgent);
			}
		}
		catch(FIPAException e)
		{
			e.printStackTrace();
		}
		
		addBehaviour(new TickerDayWaiver(this));
	}

	@Override
	protected void takeDown() {
		System.out.println("Supplier-Agent " + getAID().getName() + " terminating.");
		try {
			DFService.deregister(this);
		} catch (FIPAException e) {
			e.printStackTrace();
		}

	}
	
	
	public class TickerDayWaiver extends CyclicBehaviour {
		public TickerDayWaiver(Agent a)
		{
			super(a);
		}
		
		@Override
		public void action()
		{
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("New Day"),
			MessageTemplate.MatchContent("terminate"));
			ACLMessage msg = myAgent.receive(mt);
			//System.out.println("Inside TickerDayWaiver" + msg);
			
			if(msg != null)
			{
				if(tickerAgent == null)				{
					tickerAgent = msg.getSender();
				}
				if(msg.getContent().equals("New Day"))				{
					//cyclicBehaviours.clear();
					ArrayList<Behaviour> cyclicBehaviours = new ArrayList<>();
					day++;
					//System.out.println("Into IF at TickerWaiver");
					myAgent.addBehaviour(new GetStockSupplier());
					CyclicBehaviour getRequest = new GetRequestFromManufactures();
					myAgent.addBehaviour(getRequest);
					cyclicBehaviours.add(getRequest);
					CyclicBehaviour sellProducts = new SellingProducts();
					myAgent.addBehaviour(sellProducts);
					cyclicBehaviours.add(sellProducts);
					myAgent.addBehaviour(new EndDay(myAgent, cyclicBehaviours));
					
				}else{
					myAgent.doDelete();
				}
			}else{
				block();
			}
		}


	}
	
	
	public class GetStockSupplier extends OneShotBehaviour
	{

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public void action() 
		{
			/*
			 * 		 
			 * The item ID will be assigned as follow
			 *  1. Screen 5'
			 * 	2. Screen 7'
			 *  3. Storage 64Gb
			 *  4. Storage 256Gb
			 *  5. Memory 4Gb
			 *  6. Memory 8Gb
			 *  7. Battery 2000mAh
			 *  8. Battery 3000mAh
			 * 
			 * */
			
			components.clear();

			if (getAID().getName().contains("Supplier1")) {
				Screen screen = new Screen();
				screen.setSize(5);
				screen.setItemID(1);
				components.put(screen.getItemID(), 100);
				Screen screen2 = new Screen();
				screen2.setSize(7);
				screen2.setItemID(2);
				components.put(screen2.getItemID(), 150);
				
				Storage storage = new Storage();
				storage.setSize(64);
				storage.setItemID(3);
				components.put(storage.getItemID(), 25);
				Storage storage2 = new Storage();
				storage2.setSize(256);
				storage2.setItemID(4);
				components.put(storage2.getItemID(), 50);
				
				Memory memory = new Memory();
				memory.setSize(4);
				memory.setItemID(5);
				components.put(memory.getItemID(),30);
				Memory memory2 = new Memory();
				memory2.setSize(8);
				memory2.setItemID(6);
				components.put(memory2.getItemID(),60);
				
				Battery battery = new Battery();
				battery.setSize(2000);
				battery.setItemID(7);
				components.put(battery.getItemID(),70);
				Battery battery2 = new Battery();
				battery2.setSize(3000);
				battery2.setItemID(8);
				components.put(battery2.getItemID(),100);
				
				deliveryTime = 1;
				System.out.println("Product assigned to Supplier1");
				//System.out.println(components);
			} else {
				Storage storage = new Storage();
				storage.setSize(64);
				storage.setItemID(3);
				components.put(storage.getItemID(), 15);
				Storage storage2 = new Storage();
				storage2.setSize(256);
				storage2.setItemID(4);
				components.put(storage2.getItemID(), 40);
				
				Memory memory = new Memory();
				memory.setSize(4);
				memory.setItemID(5);
				components.put(memory.getItemID(),20);
				Memory memory2 = new Memory();
				memory2.setSize(8);
				memory2.setItemID(6);
				components.put(memory2.getItemID(),35);

				deliveryTime = 4;
				System.out.println("Product assigned to Supplier2");

			}
				
		}
		
	}
	public class GetRequestFromManufactures extends CyclicBehaviour
	{

		@Override
		public void action() 
		{
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF);
			ACLMessage msg = myAgent.receive(mt);
			System.out.println("Inside GetRequestFromManufactures at Supplier Agent");
			System.out.println(msg);
			if(msg != null)
			{
				try
				{
					ContentElement ce  = null;
					
					ce = getContentManager().extractContent(msg);
					if (ce instanceof Buy)
					{
						Buy toSell = (Buy) ce;
						Item item = toSell.getItem();
						
						Buy product = new Buy();
						
						ACLMessage reply = new ACLMessage(ACLMessage.CFP);
						reply.addReceiver(msg.getSender());
						reply.setLanguage(codec.getName());
						reply.setOntology(ontology.getName());
						
						System.out.println("Print Item " + item);
						System.out.println("Print Components: " + components);
						
						if(components.containsKey(item.getItemID())) //Check This!!!!
						{
							System.out.println("if Happens");
							product.setItem(item);
							product.setOwner(getAID());
							product.setPrice(components.get(item.getItemID())); //Check This!!!
							product.setShipmentSpeed(deliveryTime);
							
							getContentManager().fillContent(reply, product);
							send(reply);
							
							
						}
						else
						{
							System.out.println("else Happens");
							ACLMessage refuse = new ACLMessage(ACLMessage.REFUSE);
							refuse.addReceiver(msg.getSender());
							refuse.setContent("The item is not in our Stock");
							send(refuse);
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
			else
			{
				System.out.println("Block at GetRequestFromManufactures at Supplier Agent");
				block();
			}
		}
		
	}
	
	public class SellingProducts extends CyclicBehaviour
	{

		@Override
		public void action() 
		{
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage msg = myAgent.receive(mt);
			System.out.println("Inside Selling Products at Supplier Agent");
			System.out.println(msg);
			
			doWait(5000);
			if(msg != null)
			{
				try
				{
					ContentElement ce  = null;
					
					ce = getContentManager().extractContent(msg);
					if(ce instanceof Action)
					{
						Concept action = ((Action)ce).getAction();
						if(action instanceof Sell)
						{
							Sell sell = (Sell)action;
							
							if(components.containsKey(sell.getItem().getItemID())) //Check This
							{
								ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
								reply.addReceiver(sell.getBuyer());
								reply.setLanguage(codec.getName());
								reply.setOntology(ontology.getName());
								
								sell.setDeliveryDate(deliveryTime+day);
								sell.setPrice(components.get(sell.getItem().getItemID()) * sell.getQuantity()); //Check This
								
								Action myReply = new Action();
								myReply.setAction(sell);
								myReply.setActor(getAID());
								
								getContentManager().fillContent(reply, myReply);
								send(reply);
							}
							else
							{
								ACLMessage fail = new ACLMessage(ACLMessage.FAILURE);
								fail.addReceiver(sell.getBuyer());
								myAgent.send(fail);
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
		}
		
	}
	public class EndDay extends CyclicBehaviour {
		private List<Behaviour> toRemove;
		
		public EndDay(Agent a, List<Behaviour> toRemove)
		{
			super(a);
			this.toRemove = toRemove;
		}
		
		@Override
		public void action()
		{
			System.out.println("Reach EndDay at SupplierAgent");
			MessageTemplate mt = MessageTemplate.MatchContent("done");
			ACLMessage msg = myAgent.receive(mt);
			System.out.println(msg);
			if(msg != null)
			{
				System.out.println("Message is not null at EndDay SupplierAgent. Sender is: "+msg.getSender().getName());
				System.out.println("Manufacture Agent: " + manufacturerAgent);
				if(msg.getSender().equals(manufacturerAgent))
				{
					//we are finished
					System.out.println("Go into Supplier Agent is Done");
					ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
					tick.setContent("done");
					tick.addReceiver(tickerAgent);
					//System.out.println("Message: "+tick.getContent() + ". Reciever: " + tick.getSender().getLocalName());
					myAgent.send(tick);
					
					//remove behaviours
					for(Behaviour b : toRemove)
					{
						myAgent.removeBehaviour(b);
					}
					myAgent.removeBehaviour(this);
				}
			}
			else
			{
				block();
			}
		}
	}

	
	/*public class SendComponentsToManufacture extends OneShotBehaviour	{
		int sent = 0;
		@Override
		public void action() 
		{
			System.out.println("Inside SendComponentsToManufacture" + getAID().getName());
			for(Item key : components.g)
			{
				System.out.println("Inside Loop"+ getAID().getName());
				
				ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
				msg.setLanguage(codec.getName());
				msg.setOntology(ontology.getName());
				
				SupplierComponent supplierComp	= new SupplierComponent();
				supplierComp.setItem(key);
				supplierComp.setPrice((int)components.get(key));
			
				try 
				{
					// Let JADE convert from Java objects to string
					getContentManager().fillContent(msg, supplierComp);
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
			
			System.out.println("Finish");
			
			
		}
	}*/

}
