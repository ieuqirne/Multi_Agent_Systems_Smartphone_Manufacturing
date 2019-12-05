package smartphone_ontology.elements;

import jade.content.AgentAction;
import jade.core.AID;

public class Order implements AgentAction{

	private Long orderID;
	private AID purchaser;
	private Smartphone smartphone;
	private int price;
	private int quantity;
	private int delayFee;
	private int dueDate;
	
	
	public Long getOrderID() {
		return orderID;
	}

	public void setOrderID(Long orderID) {
		this.orderID = orderID;
	}

	public AID getPurchaser() {
		return purchaser;
	}
	
	public void setPurchaser(AID purchaser) {
		this.purchaser = purchaser;
	}
	
	public int getPrice() {
		return price;
	}
	
	public void setPrice(int price) {
		this.price = price;
	}
	
	public int getQuantity() {
		return quantity;
	}
	
	public void setQuantity(int quantity){
		this.quantity = quantity;
	}
	
	public int getDelayFee() {
		return delayFee;
	}
	
	public void setDelayFee(int delayFee){
		this.delayFee = delayFee;
	}
	
	public int getDueDate() {
		return dueDate;
	}
	
	public void setDueDate(int dueDate) {
		this.dueDate = dueDate;
	}
	
	public Smartphone getSmartphone() {
		return smartphone;
	}
	
	public void setSmartphone(Smartphone smartphone) {
		this.smartphone = smartphone;
	}
	
}