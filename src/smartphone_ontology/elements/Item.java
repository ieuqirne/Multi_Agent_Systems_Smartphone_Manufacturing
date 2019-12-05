package smartphone_ontology.elements;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;

public class Item implements Concept {

	private int itemID;

	public int getItemID() {
		return itemID;
	}

	public void setItemID(int itemID) {
		this.itemID = itemID;
	}
	

}
