package smartphone_ontology.elements;

import jade.content.Predicate;
import jade.core.AID;

public class Buy implements Predicate {
	private AID owner;
	private Item item;
	private int price;
	private int shipmentSpeed;
	
	public AID getOwner() {
		return owner;
	}
	
	public void setOwner(AID owner) {
		this.owner = owner;
	}
	
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
	
	public int getShipmentSpeed() {
		return shipmentSpeed;
	}
	
	public void setShipmentSpeed(int shipmentSpeed) {
		this.shipmentSpeed = shipmentSpeed;
	}
	
}
