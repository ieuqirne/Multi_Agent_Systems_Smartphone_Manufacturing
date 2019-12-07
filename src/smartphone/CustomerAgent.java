package smartphone;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

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
import smartphone_ontology.elements.Deliver;
import smartphone_ontology.elements.Memory;
import smartphone_ontology.elements.Order;
import smartphone_ontology.elements.Screen;
import smartphone_ontology.elements.Smartphone;
import smartphone_ontology.elements.Storage;
import smartphones_ontology.SmartphoneOntology;


public class CustomerAgent extends Agent
{
	private Codec codec = new SLCodec();
	private Ontology ontology = SmartphoneOntology.getInstance();
	private ArrayList<Behaviour> cyclicBehaviours = new ArrayList<>();
	private AID tickerAgent;
	private AID manufacturerAgent;
	private ArrayList<Long> workingOrders = new ArrayList<>();
	private int day = 1;
	
	@Override
	protected void setup()
	{
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		
		//add agents to the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Customer");
		sd.setName(getLocalName() + "-Customer-agent");
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
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("NewDay"),
					MessageTemplate.MatchContent("terminate"));
			ACLMessage msgTicker = myAgent.receive(mt);
			if(msgTicker != null)
			{
				if(tickerAgent == null)	{
					tickerAgent = msgTicker.getSender();
				}
				if(msgTicker.getContent().equals("NewDay")){
					cyclicBehaviours.clear();
					
					myAgent.addBehaviour(new GenerateOrder());
					myAgent.addBehaviour(new ReceiveAnswerFromSupplier());
					
					CyclicBehaviour gOrders = new GetOrders();
					myAgent.addBehaviour(gOrders);
					cyclicBehaviours.add(gOrders);
					
					myAgent.addBehaviour(new EndDay(cyclicBehaviours,myAgent));
					
				}else{
					myAgent.doDelete();
				}
			}else{
				block();
			}
		}
	}
		
	public class GenerateOrder extends OneShotBehaviour
	{
		private Order order = new Order();
		private Screen screen = new Screen();
		private Storage storage = new Storage();
		private Memory memory = new Memory();
		private Battery battery = new Battery();
		private Smartphone smartphone = new Smartphone();
		private int quantity;
		private int price;
		private int deliveryDue;
		private int penaltyDelay;
		private Random rand = new Random(); 
		
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
			//make phone to be purchased
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
			smartphone.setStorage(storage);
			smartphone.setMemory(memory);
			//Prepare message that will include order
			
			ACLMessage msgOrderSupplier = new ACLMessage(ACLMessage.PROPOSE);
			msgOrderSupplier.setLanguage(codec.getName());
			msgOrderSupplier.setOntology(ontology.getName());
			msgOrderSupplier.addReceiver(manufacturerAgent);

						
			// Get random generated order
			quantity = (int) Math.floor(1 + 50 * Math.random());
			price = (int) (quantity * Math.floor(100 + 500 * Math.random()));
			deliveryDue = (int) Math.floor(1 + 10 * Math.random());
			penaltyDelay = (int) (quantity * Math.floor(1 + 50 * Math.random()));

			order.setPurchaser(myAgent.getAID());
			order.setSmartphone(smartphone);
			order.setQuantity(quantity);
			order.setPrice(price);
			order.setDueDate(deliveryDue + day);
			order.setDelayFee(penaltyDelay);
			order.setOrderID(Math.abs(rand.nextLong()));
			System.out.println("Order--> Quantity: " + order.getQuantity() + ", Screen: " + screen.getSize() +"\", Storage: " + storage.getSize() + "Gb, Memory: " 
											+ memory.getSize() + "Gb, Battery: " + battery.getSize() + "mAh, DelayFee: " + penaltyDelay +", Due Date: " + deliveryDue);
						
			Action orderToSupplier = new Action();
			orderToSupplier.setAction(order);
			orderToSupplier.setActor(manufacturerAgent);	
			
			try	{
				getContentManager().fillContent(msgOrderSupplier, orderToSupplier);
				send(msgOrderSupplier);
			}catch (CodecException ce){
				ce.printStackTrace();
			}catch (OntologyException oe) 
			{
				oe.printStackTrace();
			} 
			
		}
	}
	
	
	public class ReceiveAnswerFromSupplier extends OneShotBehaviour{
		@Override
		public void action(){
			MessageTemplate mt = MessageTemplate.MatchConversationId("ManufactureAnswerToCustomer");
			ACLMessage answerFromSuppliers = myAgent.receive(mt);
			if(answerFromSuppliers != null){
				if(answerFromSuppliers.getPerformative() == ACLMessage.ACCEPT_PROPOSAL){
					try	{
						ContentElement ce = null;
						
						ce = getContentManager().extractContent(answerFromSuppliers);
						if(ce instanceof Action){
							Concept action = ((Action)ce).getAction();
							if(action instanceof Order){
								Order order = (Order)action;
								workingOrders.add(order.getOrderID());	
							}
						}
					}catch (CodecException ce){
						ce.printStackTrace();
					}catch (OntologyException oe){
						oe.printStackTrace();
					}
				}else{
					return;
				}
			}else{
				block();
			}
		}
	}
	
	public class GetOrders extends CyclicBehaviour
	{
		int orderAmount;
		int orderQuantity;
		int orderPrice;
		
		public void action(){
			
			System.out.println("Inside GetOrders at Customer");
			
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
			ACLMessage msgGetOrders = myAgent.receive(mt);
			if(msgGetOrders != null)	{
				try	{
					ContentElement ce = null;
					ce = getContentManager().extractContent(msgGetOrders);
					if (ce instanceof Action){
						Concept action = ((Action)ce).getAction();
						if(action instanceof Deliver){
							Deliver delivery = (Deliver)action;
							
							if(workingOrders.contains(delivery.getOrder().getOrderID())){
								ACLMessage msgPayment = new ACLMessage(ACLMessage.INFORM);
								msgPayment.setConversationId("PaymentFromCustomerToManu");
								msgPayment.addReceiver(msgGetOrders.getSender());

								orderQuantity = delivery.getOrder().getQuantity();
								orderPrice = delivery.getOrder().getPrice();
								orderAmount = orderPrice * orderQuantity;
								
								msgPayment.setContent(Integer.toString(orderAmount));
								
								myAgent.send(msgPayment);
								System.out.println("Delivery to delete " + delivery.getOrder().getOrderID());
								System.out.println("workingOrders: " + workingOrders);
								workingOrders.remove(delivery.getOrder().getOrderID());
							}else{
								ACLMessage msgWrong = new ACLMessage(ACLMessage.FAILURE);
								msgWrong.setConversationId("PaymentFromCustomerToManu");
								msgWrong.setContent("PaymentWrong");
								msgWrong.addReceiver(msgGetOrders.getSender());

								myAgent.send(msgWrong);
							}
						}
					}
				}catch (CodecException ce) {
					ce.printStackTrace();
				}catch (OntologyException oe) {
					oe.printStackTrace();
				}
			}else{
				block();
			}
		}
	}
	
	
	public class EndDay extends CyclicBehaviour{
		private List<Behaviour> cyclicB;
		
		public EndDay(List<Behaviour> cyclicB, Agent a){
			super(a);
			this.cyclicB = cyclicB;
		}
		
		@Override
		public void action(){
			MessageTemplate mt = MessageTemplate.MatchContent("done");
			ACLMessage msgEndDay = myAgent.receive(mt);
			if(msgEndDay != null)	{
				if(msgEndDay.getSender().equals(manufacturerAgent)){
					//we are finished
					ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
					tick.setContent("done");
					tick.addReceiver(tickerAgent);
					myAgent.send(tick);
					day++;
					for(Behaviour behaviour : cyclicB){
						myAgent.removeBehaviour(behaviour);
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