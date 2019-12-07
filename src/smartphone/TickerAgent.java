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


public class TickerAgent extends Agent{
	
public static final int NUM_DAYS = 10;
	
	@Override
	protected void setup(){
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Ticker-Agent");
		sd.setName(getLocalName() + "-Ticker-Agent");
		dfd.addServices(sd);
		try	{
			DFService.register(this, dfd);
		} catch (FIPAException e){
			e.printStackTrace();
		}
		
		//doWait is uses to wait for all the agent to start
		doWait(15000);
		addBehaviour(new SynchAgentsBehaviour(this));
	}
	
	@Override
	protected void takeDown()
	{
		try{
			DFService.deregister(this);
		}catch (FIPAException e){
			e.printStackTrace();
		}
	}
	
	public class SynchAgentsBehaviour extends Behaviour
	{
		
		private int numFinReceived = 0;
		private int day = 0;
		private int caseVa = 0;
		private ArrayList<AID> allAgents = new ArrayList<>();
		
		public SynchAgentsBehaviour(Agent a){
			super(a);
		}
		
		@Override
		public void action()
		{
			switch(caseVa){
				case 0:
					allAgents.clear();
					
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
					
					try	{
						DFAgentDescription[] agentsType1 = DFService.search(myAgent, template1);
						for(int i=0; i < agentsType1.length; i++){
							allAgents.add(agentsType1[i].getName());
						}
						DFAgentDescription[] agentsType2 = DFService.search(myAgent, template2);
						for(int i=0; i < agentsType2.length; i++){
							allAgents.add(agentsType2[i].getName());
						}
						DFAgentDescription[] agentsType3 = DFService.search(myAgent, template3);
						for(int i=0; i < agentsType3.length; i++){
							allAgents.add(agentsType3[i].getName());
						}	
					}catch(FIPAException e)	{
						e.printStackTrace();
					}
					
					//send new day message to each agent
					ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
					tick.setContent("NewDay");
					for(AID id : allAgents){
						tick.addReceiver(id);
					}
					
					myAgent.send(tick);
					caseVa++;
					day++;
					break;
					
					
				case 1:
					//wait to receive a "done" message from all agents
					MessageTemplate mt = MessageTemplate.MatchContent("done");
					ACLMessage msg = myAgent.receive(mt);
					if(msg != null){
						numFinReceived++;
						if(numFinReceived >= allAgents.size()){
							caseVa++;
						}
					}else{
						block();
					}
			}
		}
		
		@Override
		public boolean done(){
			return caseVa == 2;
		}
		
		@Override
		public void reset(){
			super.reset();
			caseVa = 0;
			//allAgents.clear();
			numFinReceived = 0;
		}
		
		@Override
		public int onEnd(){
			System.out.println("");
			System.out.println("End of day " + day);
			if(day == NUM_DAYS){
				//send termination message to each agent
				/*ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
				msg.setContent("terminate");
				for(AID agent : allAgents)
				{
					msg.addReceiver(agent);
				}
				myAgent.send(msg);*/
				myAgent.doDelete();
			}else{
				reset();
				myAgent.addBehaviour(this);
			}
			return 0;
		}
	}
}