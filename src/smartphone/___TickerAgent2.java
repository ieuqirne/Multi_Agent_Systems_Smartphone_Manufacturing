package smartphone;

import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class ___TickerAgent2 extends Agent {
	
	public static final int NUM_DAYS = 5;
	@Override
	protected void setup() {
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Ticker-agent");
		sd.setName(getLocalName() + "-Ticker-Agent");
		dfd.addServices(sd);
		try{
			DFService.register(this, dfd);
		}
		catch(FIPAException e){
			e.printStackTrace();
		}
		//wait for the other agents to start
		doWait(15000);
		addBehaviour(new SynchAgentsBehaviour(this));
	}

	@Override
	protected void takeDown() {
		try{
			DFService.deregister(this);
		}
		catch(FIPAException e){
			e.printStackTrace();
		}
	}

	public class SynchAgentsBehaviour extends Behaviour {

		private int step = 0;
		private int numFinReceived = 0; //finished messages from other agents
		private int day = 0;
		private ArrayList<AID> simulationAgents = new ArrayList<>();
		/**
		 * @param a	the agent executing the behaviour
		 */
		public SynchAgentsBehaviour(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			switch(step) {
			case 0:
				System.out.println("Inside SynchAgentsBehaviour Case 0");
				//find all agents using directory service
				DFAgentDescription template1 = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("Customer");
				template1.addServices(sd);
				
				DFAgentDescription template2 = new DFAgentDescription();
				ServiceDescription sd2 = new ServiceDescription();
				sd2.setType("Manufacturer");
				template2.addServices(sd2);
				
				DFAgentDescription template3 = new DFAgentDescription();
				ServiceDescription sd3 = new ServiceDescription();
				sd3.setType("Supplier");
				template3.addServices(sd3);
				
				try{
					DFAgentDescription[] agentsType1  = DFService.search(myAgent,template1); 
					for(int i=0; i<agentsType1.length; i++){
						simulationAgents.add(agentsType1[i].getName()); // this is the AID
						System.out.println("Agent: " + agentsType1[i]);
					}
					DFAgentDescription[] agentsType2  = DFService.search(myAgent,template2); 
					for(int i=0; i<agentsType2.length; i++){
						simulationAgents.add(agentsType2[i].getName()); // this is the AID
						System.out.println("Agent: " + agentsType1[i]);
					}
					DFAgentDescription[] agentsType3  = DFService.search(myAgent,template3); 
					for(int i=0; i<agentsType3.length; i++){
						simulationAgents.add(agentsType3[i].getName()); // this is the AID
						System.out.println("Agent: " + agentsType1[i]);
					}
				}
				catch(FIPAException e) {
					e.printStackTrace();
				}
				//send new day message to each agent
				ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
				tick.setContent("New Day");
				for(AID id : simulationAgents) {
					tick.addReceiver(id);
				}
				myAgent.send(tick);
				step++;
				day++;
				break;
			case 1:
				System.out.println("Inside SynchAgentsBehaviour Case 1");
				MessageTemplate mt = MessageTemplate.MatchContent("done");
				ACLMessage msg = myAgent.receive(mt);
				
				//System.out.println("Receiver " + msg.getSender());
				System.out.println("SumulationAgents: " + simulationAgents.size());
				System.out.println("Num Fin Received: " + numFinReceived);
				//doWait(5000);
				if(msg != null)
				{
					numFinReceived++;
					if(numFinReceived >= simulationAgents.size())
					{
						step++;
					}
				}
				else
				{
					block();
				}

			}
		}

		@Override
		public boolean done() {
			return step == 2;
		}
		
		
		@Override
		public void reset() {
			super.reset();
			step = 0;
			simulationAgents.clear();
			numFinReceived = 0;
		}

		
		@Override
		public int onEnd() {
			System.out.println("End of day " + day);
			if(day == NUM_DAYS) {
				//send termination message to each agent
				ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
				msg.setContent("terminate");
				for(AID agent : simulationAgents) {
					msg.addReceiver(agent);
				}
				myAgent.send(msg);
				myAgent.doDelete();
			}
			else {
				reset();
				myAgent.addBehaviour(this);
			}
			
			return 0;
		}
	}
}
