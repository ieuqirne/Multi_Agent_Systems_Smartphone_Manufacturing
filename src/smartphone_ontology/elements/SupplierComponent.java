package smartphone_ontology.elements;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import jade.content.AgentAction;
import jade.content.Concept;
import jade.content.Predicate;
import jade.content.onto.annotations.AggregateSlot;

public class SupplierComponent implements Predicate{
	/**
	 * 
	 */
	private Item item;
	private int price;
	
	
	public Item getItem() {
		return item;
	}
	public void setItem(Item item) {
		this.item = item;
	}
	public int getPrice() {
		return price;
	}
	public void setPrice(int price) {
		this.price = price;
	}
		
}



