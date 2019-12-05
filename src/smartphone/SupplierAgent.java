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
import smartphones_ontology.SmartphoneOntology;


public class SupplierAgent extends Agent
{
	private Codec codec = new SLCodec();
	private Ontology ontology = SmartphoneOntology.getInstance();
	
	private AID tickerAgent;
	private AID manufacturer;
	
	private HashMap<Integer, Integer> itemsForSale = new HashMap<>();
	private int deliverySpeed = 0;
	private int day = 0;
	
	@Override
	protected void setup()
	{
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		
		//add agents to the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("supplier");
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
		manufacSD.setType("manufacturer");
		manufacturerTemplate.addServices(manufacSD);
		try
		{
			DFAgentDescription[] agent = DFService.search(this, manufacturerTemplate);
			for(int i = 0; i<agent.length; i++)
			{
				manufacturer = agent[i].getName();
				System.out.println("Manufacture Agent: " + manufacturer);
			}
		}
		catch(FIPAException e)
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
					day++;
					/*
					 * Add customer behaviours here
					 */
					myAgent.addBehaviour(new GetStock());
					doWait(5000);
					System.out.println("Components: " + itemsForSale);
					CyclicBehaviour ol = new OwnsListener();
					myAgent.addBehaviour(ol);
					CyclicBehaviour sl = new SellListener();
					myAgent.addBehaviour(sl);
					ArrayList<Behaviour> cyclicBehaviours = new ArrayList<>();
					cyclicBehaviours.add(ol);
					cyclicBehaviours.add(sl);
					myAgent.addBehaviour(new EndDayListener(myAgent, cyclicBehaviours));
					
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
	
	
	public class GetStock extends OneShotBehaviour
	{
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
			
			itemsForSale.clear();

			if (getAID().getName().contains("supplier1")) {
				Screen screen = new Screen();
				screen.setSize(5);
				screen.setItemID(1);
				itemsForSale.put(screen.getItemID(), 100);
				Screen screen2 = new Screen();
				screen2.setSize(7);
				screen2.setItemID(2);
				itemsForSale.put(screen2.getItemID(), 150);
				
				Storage storage = new Storage();
				storage.setSize(64);
				storage.setItemID(3);
				itemsForSale.put(storage.getItemID(), 25);
				Storage storage2 = new Storage();
				storage2.setSize(256);
				storage2.setItemID(4);
				itemsForSale.put(storage2.getItemID(), 50);
				
				Memory memory = new Memory();
				memory.setSize(4);
				memory.setItemID(5);
				itemsForSale.put(memory.getItemID(),30);
				Memory memory2 = new Memory();
				memory2.setSize(8);
				memory2.setItemID(6);
				itemsForSale.put(memory2.getItemID(),60);
				
				Battery battery = new Battery();
				battery.setSize(2000);
				battery.setItemID(7);
				itemsForSale.put(battery.getItemID(),70);
				Battery battery2 = new Battery();
				battery2.setSize(3000);
				battery2.setItemID(8);
				itemsForSale.put(battery2.getItemID(),100);
				
				deliverySpeed = 1;
				System.out.println("Product assigned to Supplier1");
				//System.out.println(components);
			} else {
				Storage storage = new Storage();
				storage.setSize(64);
				storage.setItemID(3);
				itemsForSale.put(storage.getItemID(), 15);
				Storage storage2 = new Storage();
				storage2.setSize(256);
				storage2.setItemID(4);
				itemsForSale.put(storage2.getItemID(), 40);
				
				Memory memory = new Memory();
				memory.setSize(4);
				memory.setItemID(5);
				itemsForSale.put(memory.getItemID(),20);
				Memory memory2 = new Memory();
				memory2.setSize(8);
				memory2.setItemID(6);
				itemsForSale.put(memory2.getItemID(),35);

				deliverySpeed = 4;
				System.out.println("Product assigned to Supplier2");
			}
				
		}
		
	}
	
	
	public class OwnsListener extends CyclicBehaviour
	{

		@Override
		public void action() 
		{
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null)
			{
				try
				{
					ContentElement ce  = null;
					
					ce = getContentManager().extractContent(msg);
					if (ce instanceof Buy)
					{
						Buy owns = (Buy) ce;
						Item comp = owns.getItem();
						
						Buy owner = new Buy();
						System.out.println("Get Item id: " + owns.getItem().getItemID());
						ACLMessage reply = new ACLMessage(ACLMessage.CFP);
						reply.addReceiver(msg.getSender());
						reply.setLanguage(codec.getName());
						reply.setOntology(ontology.getName());
						
						if(itemsForSale.containsKey(comp.getItemID()))
						{
							owner.setItem(comp);
							owner.setOwner(getAID());
							owner.setPrice(itemsForSale.get(comp.getItemID()));
							owner.setShipmentSpeed(deliverySpeed);
							
							getContentManager().fillContent(reply, owner);
							send(reply);
							
							
						}
						else
						{
							ACLMessage refuse = new ACLMessage(ACLMessage.REFUSE);
							refuse.addReceiver(msg.getSender());
							refuse.setContent("We do not sell that item");
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
				block();
			}
		}
		
	}
	
	
	public class SellListener extends CyclicBehaviour
	{

		@Override
		public void action() 
		{
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage msg = myAgent.receive(mt);
			
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
							
							if(itemsForSale.containsKey(sell.getItem().getItemID()))
							{
								ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
								reply.addReceiver(sell.getBuyer());
								reply.setLanguage(codec.getName());
								reply.setOntology(ontology.getName());
								
								sell.setDeliveryDate(deliverySpeed+day);
								sell.setPrice(itemsForSale.get(sell.getItem().getItemID()) * sell.getQuantity());
								
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
	
	
	public class EndDayListener extends CyclicBehaviour
	{
		private List<Behaviour> toRemove;
		
		public EndDayListener(Agent a, List<Behaviour> toRemove)
		{
			super(a);
			this.toRemove = toRemove;
		}
		
		@Override
		public void action()
		{
			MessageTemplate mt = MessageTemplate.MatchContent("done");
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null)
			{
				
				if(msg.getSender().equals(manufacturer))
				{
					//we are finished
					ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
					tick.setContent("done");
					tick.addReceiver(tickerAgent);
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
}