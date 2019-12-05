package smartphone;

import java.util.ArrayList;
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
import smartphone_ontology.elements.*;
import smartphones_ontology.SmartphoneOntology;

public class ___CustomerAgent extends Agent {

	
	private Codec codec = new SLCodec();
	private Ontology ontology = SmartphoneOntology.getInstance();
	private AID tickerAgent;
	private AID manufacturersAgent;
	int day = 0;
	private ArrayList<Long> workingOrder = new ArrayList<>();

	protected void setup() {
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		
		//add agents to the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Customer");
		sd.setName(getLocalName() + "-Customer-Agent");
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
				manufacturersAgent = agent[i].getName();
			}
		}
		catch(FIPAException e)
		{
			e.printStackTrace();
		}
		addBehaviour(new TickerWaiter(this));
	}

	@Override
	protected void takeDown() {
		System.out.println("Customer-Agent " + getAID().getName() + " terminating.");
		try {
			DFService.deregister(this);
		} catch (FIPAException e) {
			e.printStackTrace();
		}

	}

	public class TickerWaiter extends CyclicBehaviour {
		public TickerWaiter(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("New Day"),
					MessageTemplate.MatchContent("terminate"));
			ACLMessage msg = myAgent.receive(mt);
			ArrayList<Behaviour> cyclicBehaviours = new ArrayList<>();
			if (msg != null) {
				if (tickerAgent == null) {
					tickerAgent = msg.getSender();
				}
				if (msg.getContent().equals("New Day")) {
					cyclicBehaviours.clear();
					day++;
					
					myAgent.addBehaviour(new GenerateOrders());
					myAgent.addBehaviour(new ReceiveAnswerFromManufacture());
					CyclicBehaviour orderManufacture = new GetOrderFromManufacture();
					myAgent.addBehaviour(orderManufacture);
					cyclicBehaviours.add(orderManufacture);
					myAgent.addBehaviour(new EndDay(myAgent,cyclicBehaviours));
				} else {
					// termination message to end simulation
					myAgent.doDelete();
				}
			} else {
				block();
			}
		}
	}
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
	public class GenerateOrders extends OneShotBehaviour {
		public void action() {
			Order order = new Order();
			Screen screen = new Screen();
			Storage storage = new Storage();
			Memory memory = new Memory();
			Battery battery = new Battery();
			Smartphone smartphone = new Smartphone();
			int quantity;
			int price;
			int deliveryDue;
			int penaltyDelay;

			ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
			msg.addReceiver(manufacturersAgent); // sellerAID is the AID of the Seller agent
			msg.setLanguage(codec.getName());
			msg.setOntology(ontology.getName());
			
			// Get randomly the different pieces
			// Screen and Battery
			if (Math.random() < 0.5) {
				screen.setSize(5);
				screen.setItemID(1);
				battery.setSize(2000);
				battery.setItemID(7);
				smartphone.setName("Phone");
			} else {
				screen.setSize(7);
				screen.setItemID(2);
				battery.setSize(3000);
				battery.setItemID(8);
				smartphone.setName("Tablet");
			}
			// Storage
			if (Math.random() < 0.5) {
				storage.setSize(64);
				storage.setItemID(3);

			} else {
				storage.setSize(256);
				storage.setItemID(4);
			}

			// Memory
			if (Math.random() < 0.5) {
				memory.setSize(4);
				memory.setItemID(5);

			} else {
				memory.setSize(8);
				memory.setItemID(6);
			}

			smartphone.setScreen(screen);
			smartphone.setBattery(battery);
			smartphone.setMemory(memory);
			smartphone.setStorage(storage);

			// Get random generated order
			quantity = (int) Math.floor(1 + 50 * Math.random());
			price = (int) (quantity * Math.floor(100 + 500 * Math.random()));
			deliveryDue = (int) Math.floor(1 + 10 * Math.random());
			penaltyDelay = (int) (quantity * Math.floor(1 + 50 * Math.random()));

			order.setPurchaser(myAgent.getAID());
			order.setSmartphone(smartphone);
			order.setQuantity(quantity);
			order.setPrice(price);
			order.setDueDate(deliveryDue);
			order.setDelayFee(penaltyDelay);
			System.out.println("Order--> Quantity: " + order.getQuantity() + ", Screen: " + screen.getSize() +"\", Storage: " + storage.getSize() + "Gb, Memory: " 
								+ memory.getSize() + "Gb, Battery: " + battery.getSize() + "mAh, DelayFee: " + penaltyDelay +", Due Date: " + deliveryDue);
			
			// IMPORTANT: According to FIPA, we need to create a wrapper Action object
			// with the action and the AID of the agent
			// we are requesting to perform the action
			// you will get an exception if you try to send the sell action directly
			// not inside the wrapper!!!
			Action request = new Action();
			request.setAction(order);
			request.setActor(manufacturersAgent); // the agent that you request to perform the action
			try {
				// Let JADE convert from Java objects to string
				getContentManager().fillContent(msg, request); // send the wrapper object
				send(msg);
			} catch (CodecException ce) {
				ce.printStackTrace();
			} catch (OntologyException oe) {
				oe.printStackTrace();
			}
		}
	}

	/*
	 * public class CheckManufacturers extends OneShotBehaviour { public
	 * CheckManufacturers(Agent a) { super(a); }
	 * 
	 * @Override public void action() { DFAgentDescription manuTemplate = new
	 * DFAgentDescription(); ServiceDescription sd = new ServiceDescription();
	 * manuTemplate.addServices(sd); try { manufacturersAgent.clear();
	 * DFAgentDescription[] agentType1 = DFService.search(myAgent, manuTemplate);
	 * for (int i = 0; i < agentType1.length; i++) {
	 * manufacturersAgents.add(agentType1[i].getName()); } } catch (FIPAException e)
	 * { e.printStackTrace(); } } }
	 */
	
	public class ReceiveAnswerFromManufacture extends OneShotBehaviour
	{
		
		@Override
		public void action() 
		{
			MessageTemplate mt = MessageTemplate.MatchConversationId("order-reply");
			ACLMessage reply = myAgent.receive(mt);
			if(reply != null)
			{
				if(reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL)
				{
					try
					{
						ContentElement ce = null;
						
						ce = getContentManager().extractContent(reply);
						if(ce instanceof Action)
						{
							Concept action = ((Action)ce).getAction();
							if(action instanceof Order)
							{
								Order order = (Order)action;
								workingOrder.add(order.getOrderID());
								
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
					return;
				}
			}
			else
			{
				block();
			}
			
		}
		
	}
	
	public class GetOrderFromManufacture extends CyclicBehaviour
	{

		public void action() 
		{
			System.out.println("Inside GetOrderFromManufacture at Customer");
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
			ACLMessage msg = myAgent.receive(mt);
			System.out.println(msg);
			if(msg != null)
			{
				try
				{
					ContentElement ce = null;
					
					ce = getContentManager().extractContent(msg);
					if (ce instanceof Action)
					{
						Concept action = ((Action)ce).getAction();
						if(action instanceof Deliver)
						{
							Deliver delivery = (Deliver)action;
							
							if(workingOrder.contains(delivery.getOrder().getOrderID()))
							{
								ACLMessage payment = new ACLMessage(ACLMessage.INFORM);
								payment.addReceiver(msg.getSender());
								payment.setConversationId("Order payment");
								
								int totalPrice = delivery.getOrder().getPrice() * delivery.getOrder().getQuantity();
								
								payment.setContent(Integer.toString(totalPrice));
								
								myAgent.send(payment);
								workingOrder.remove(delivery.getOrder().getOrderID());
							}
							else
							{
								ACLMessage failure = new ACLMessage(ACLMessage.FAILURE);
								failure.addReceiver(msg.getSender());
								failure.setConversationId("Order payment");
								failure.setContent("Wrong order received");
								
								myAgent.send(failure);
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
			else
			{
				block();
			}
		}
		
	}
	
	public class EndDay extends CyclicBehaviour{
		private List<Behaviour> toRemove;
		
		public EndDay(Agent a, List<Behaviour> toRemove){
			super(a);
			this.toRemove = toRemove;
		}
		
		@Override
		public void action(){
			System.out.println("Reach EndDay At Customer");
			MessageTemplate mt = MessageTemplate.MatchContent("done");
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null)
			{
				if(msg.getSender().equals(manufacturersAgent)){
					ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
					tick.setContent("done");
					tick.addReceiver(tickerAgent);
					myAgent.send(tick);
					
					//remove behaviours
					for(Behaviour b : toRemove){
						myAgent.removeBehaviour(b);
					}
					myAgent.removeBehaviour(this);
				}
			}else{
				block();
			}
		}
	}
}
