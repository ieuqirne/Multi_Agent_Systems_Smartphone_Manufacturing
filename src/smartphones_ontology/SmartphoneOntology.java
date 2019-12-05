package smartphones_ontology;

import jade.content.onto.BeanOntology;
import jade.content.onto.BeanOntologyException;
import jade.content.onto.Ontology;

public class SmartphoneOntology extends BeanOntology{
	
	private static Ontology theInstance = new SmartphoneOntology("my_ontology");
	
	public static Ontology getInstance(){
		return theInstance;
	}
	//singleton pattern
	private SmartphoneOntology(String name) {
		super(name);
		try {
			add("smartphone_ontology.elements");
		} catch (BeanOntologyException e) {
			e.printStackTrace();
		}
	}
}
