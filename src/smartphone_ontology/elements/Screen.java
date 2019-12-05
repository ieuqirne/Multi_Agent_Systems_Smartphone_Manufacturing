package smartphone_ontology.elements;

import jade.content.onto.annotations.Slot;

public class Screen extends Item {
	
	private int size;
	@Slot (mandatory = true)
	public int getSize() {
		return size;
	}
	
	public void setSize(int size) {
		this.size = size;
	}
}
