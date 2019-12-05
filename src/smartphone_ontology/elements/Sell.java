package smartphone_ontology.elements;

import jade.content.AgentAction;
import jade.core.AID;

public class Sell implements AgentAction {
	private AID buyer;
	private Item item;
	private int quantity;
	private int price;
	private int deliveryDate;
	
	public AID getBuyer() {
		return buyer;
	}
	
	public void setBuyer(AID buyer) {
		this.buyer = buyer;
	}
	
	public Item getItem() {
		return item;
	}
	
	public void setItem(Item item) {
		this.item = item;
	}	
	
	public int getQuantity() {
		return quantity;
	}
	
	public void setQuantity(int quantity){
		this.quantity = quantity;
	}
	
	public int getPrice() {
		return price;
	}
	
	public void setPrice(int price) {
		this.price = price;
	}
	
	public int getDeliveryDate() {
		return deliveryDate;
	}
	
	public void setDeliveryDate(int deliveryDate) {
		this.deliveryDate = deliveryDate;
	}
}
