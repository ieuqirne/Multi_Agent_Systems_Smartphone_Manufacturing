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
	//private Comparator comp = new Comparator();
	private AID tickerAgent;
	private AID manufacturerAgent;
	private ArrayList<Behaviour> cyclicBehaviours = new ArrayList<>();
	private HashMap<Integer, Integer> supplierStock = new HashMap<>();
	private int shipmentSpeed = 0;
	private int day = 1;
	
	@Override
	protected void setup(){
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		
		//add agents to the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Supplier");
		sd.setName(getLocalName() + "-Supplier-Agent");
		dfd.addServices(sd);
		try{
			DFService.register(this, dfd);
		}catch (FIPAException e){
			e.printStackTrace();
		}
		
		
		DFAgentDescription manufacturerTemplate = new DFAgentDescription();
		ServiceDescription manufacSD = new ServiceDescription();
		manufacSD.setType("Manufacturer");
		manufacturerTemplate.addServices(manufacSD);
		try{
			DFAgentDescription[] agent = DFService.search(this, manufacturerTemplate);
			for(int i = 0; i<agent.length; i++){
				manufacturerAgent = agent[i].getName();
				//System.out.println("Manufacture Agent: " + manufacturerAgent);
			}
		}catch(FIPAException e){
			e.printStackTrace();
		}
		addBehaviour(new TickerWaiter(this));
	}
	
	
	protected void takedown(){
		try{
			DFService.deregister(this);
		}catch (FIPAException e){
			e.printStackTrace();
		}
	}
	
	
	public class TickerWaiter extends CyclicBehaviour{
		public TickerWaiter(Agent a){
			super(a);
		}
		
		@Override
		public void action(){
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("NewDay"),
					MessageTemplate.MatchContent("terminate"));
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null)	{
				if(tickerAgent == null)	{
					tickerAgent = msg.getSender();
				}
				if(msg.getContent().equals("NewDay")){
					cyclicBehaviours.clear();
					myAgent.addBehaviour(new GetStock());
					doWait(5000);
					//System.out.println("Components: " + supplierStock);
					CyclicBehaviour getRequestM = new GetRequestFromManufacture();
					CyclicBehaviour sellToManu = new SellingItemsToManufactures();
					
					myAgent.addBehaviour(getRequestM);
					myAgent.addBehaviour(sellToManu);
					
					cyclicBehaviours.add(getRequestM);
					cyclicBehaviours.add(sellToManu);
					myAgent.addBehaviour(new EndDayListener(myAgent, cyclicBehaviours));
					
					day++;
				}else{
					myAgent.doDelete();
				}
			}else{
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
			
			supplierStock.clear();

			if (getAID().getName().contains("Supplier_1")) {
				Screen screen = new Screen();
				screen.setSize(5);
				screen.setItemID(1);
				supplierStock.put(screen.getItemID(), 100);
				Screen screen2 = new Screen();
				screen2.setSize(7);
				screen2.setItemID(2);
				supplierStock.put(screen2.getItemID(), 150);
				
				Storage storage = new Storage();
				storage.setSize(64);
				storage.setItemID(3);
				supplierStock.put(storage.getItemID(), 25);
				Storage storage2 = new Storage();
				storage2.setSize(256);
				storage2.setItemID(4);
				supplierStock.put(storage2.getItemID(), 50);
				
				Memory memory = new Memory();
				memory.setSize(4);
				memory.setItemID(5);
				supplierStock.put(memory.getItemID(),30);
				Memory memory2 = new Memory();
				memory2.setSize(8);
				memory2.setItemID(6);
				supplierStock.put(memory2.getItemID(),60);
				
				Battery battery = new Battery();
				battery.setSize(2000);
				battery.setItemID(7);
				supplierStock.put(battery.getItemID(),70);
				Battery battery2 = new Battery();
				battery2.setSize(3000);
				battery2.setItemID(8);
				supplierStock.put(battery2.getItemID(),100);
				
				shipmentSpeed = 1;
				//System.out.println("Product assigned to Supplier1");
				//System.out.println(components);
			} else {
				Storage storage = new Storage();
				storage.setSize(64);
				storage.setItemID(3);
				supplierStock.put(storage.getItemID(), 15);
				Storage storage2 = new Storage();
				storage2.setSize(256);
				storage2.setItemID(4);
				supplierStock.put(storage2.getItemID(), 40);
				
				Memory memory = new Memory();
				memory.setSize(4);
				memory.setItemID(5);
				supplierStock.put(memory.getItemID(),20);
				Memory memory2 = new Memory();
				memory2.setSize(8);
				memory2.setItemID(6);
				supplierStock.put(memory2.getItemID(),35);

				shipmentSpeed = 4;
				//System.out.println("Product assigned to Supplier2");
			}
		}	
	}
	
	public class GetRequestFromManufacture extends CyclicBehaviour
	{

		@Override
		public void action() 
		{
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null){
				try	{
					ContentElement ce  = null;
					
					ce = getContentManager().extractContent(msg);
					if (ce instanceof Buy){
						Buy buy = (Buy) ce;
						Item comp = buy.getItem();
						
						Buy buying = new Buy();
						//System.out.println("Get Item id: " + buy.getItem().getItemID());
						ACLMessage answerToManu = new ACLMessage(ACLMessage.CFP);
						answerToManu.setLanguage(codec.getName());
						answerToManu.setOntology(ontology.getName());
						answerToManu.addReceiver(msg.getSender());

						
						if(supplierStock.containsKey(comp.getItemID())){
							buying.setItem(comp);
							buying.setOwner(getAID());
							buying.setPrice(supplierStock.get(comp.getItemID()));
							buying.setShipmentSpeed(shipmentSpeed);
							
							getContentManager().fillContent(answerToManu, buying);
							send(answerToManu);
						}else{
							ACLMessage refuse = new ACLMessage(ACLMessage.REFUSE);
							refuse.setContent("Item Not Selled");
							refuse.addReceiver(msg.getSender());
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
			else{
				block();
			}
		}
	}
	
	public class SellingItemsToManufactures extends CyclicBehaviour{

		@Override
		public void action(){
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage msg = myAgent.receive(mt);
			
			if(msg != null){
				try	{
					ContentElement ce  = null;
					
					ce = getContentManager().extractContent(msg);
					if(ce instanceof Action){
						Concept action = ((Action)ce).getAction();
						if(action instanceof Sell){
							Sell sell = (Sell)action;
							
							if(supplierStock.containsKey(sell.getItem().getItemID())){
								ACLMessage answerToManu = new ACLMessage(ACLMessage.INFORM);
								answerToManu.setLanguage(codec.getName());
								answerToManu.setOntology(ontology.getName());
								answerToManu.addReceiver(sell.getBuyer());
								
								sell.setDeliveryDate(day + shipmentSpeed);
								sell.setPrice(supplierStock.get(sell.getItem().getItemID()) * sell.getQuantity());
								
								Action myReply = new Action();
								myReply.setAction(sell);
								myReply.setActor(getAID());
								
								getContentManager().fillContent(answerToManu, myReply);
								send(answerToManu);
							}else{
								ACLMessage fail = new ACLMessage(ACLMessage.FAILURE);
								fail.addReceiver(sell.getBuyer());
								myAgent.send(fail);
							}
						}
					}
				}
				catch (CodecException ce){
					ce.printStackTrace();
				}catch (OntologyException oe) {
					oe.printStackTrace();
				}
			}
		}	
	}
	
	
	public class EndDayListener extends CyclicBehaviour
	{
		private List<Behaviour> toRemove;
		
		public EndDayListener(Agent a, List<Behaviour> toRemove){
			super(a);
			this.toRemove = toRemove;
		}
		
		@Override
		public void action(){
			MessageTemplate mt = MessageTemplate.MatchContent("done");
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null){
				if(msg.getSender().equals(manufacturerAgent))
				{
					//we are finished
					ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
					tick.setContent("done");
					tick.addReceiver(tickerAgent);
					myAgent.send(tick);
					
					//remove behaviours
					for(Behaviour b : toRemove)	{
						myAgent.removeBehaviour(b);
					}
					myAgent.removeBehaviour(this);
				}
			}
			else{
				block();
			}
		}
	}
}