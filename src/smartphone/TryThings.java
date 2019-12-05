package smartphone;

import smartphone_ontology.elements.Battery;
import smartphone_ontology.elements.Memory;
import smartphone_ontology.elements.Screen;
import smartphone_ontology.elements.Smartphone;
import smartphone_ontology.elements.Storage;

public class TryThings {

	public static void main(String[] args) {
		
		Comparator compare = new Comparator();
		
		String aa = "suppli1asdfasdfasdfasdfasdf";
		
		Screen screen = new Screen();
		Screen screen2 = new Screen();
		Memory memory = new Memory();
		Memory memory2 = new Memory();
		Battery battery = new Battery();
		Battery battery2 = new Battery();
		Storage storage = new Storage();
		Storage storage2 = new Storage();
		Smartphone smartphone1 = new Smartphone();
		Smartphone smartphone2 = new Smartphone();
		screen.setSize(5);
		battery.setSize(2000);
		storage.setSize(50);
		memory.setSize(100);
		storage.setItemID(1);
		
		
		screen2.setSize(5);
		battery2.setSize(2000);
		storage2.setSize(500);
		storage2.setItemID(1);
		memory2.setSize(100);
		
		
		
		smartphone1.setScreen(screen);
		smartphone2.setScreen(screen2);
		smartphone1.setMemory(memory);
		smartphone2.setMemory(memory2);
		smartphone1.setBattery(battery);
		smartphone2.setBattery(battery2);
		smartphone1.setStorage(storage);
		smartphone2.setStorage(storage2);
		//System.out.println(smartphone1.getScreen().getSize());
		//System.out.println(smartphone2.getScreen().getSize());
		
		
		compare.phoneComp(smartphone1, smartphone2);
		compare.batteryComp(battery, battery2);
		compare.memoryComp(memory, memory2);
		compare.screenComp(screen, screen2);
		compare.storageComp(storage, storage2);
		compare.itemComp(storage, storage2);
		/*if (screen.toString().equals(screen2.toString())) {
			System.out.println("Hey!!");
		}else {
			System.out.println(screen.toString());
			System.out.println("Dont match");
		}*/
	}
}
