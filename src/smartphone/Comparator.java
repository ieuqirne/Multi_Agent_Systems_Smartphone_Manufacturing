package smartphone;

import smartphone_ontology.elements.*;


public class Comparator {
		public Boolean phoneComp(Smartphone smartphone1, Smartphone smartphone2) {
			
			/*Screen screen = new Screen();
			Storage storage = new Storage();
			Memory memory = new Memory();
			Battery battery = new Battery();
			
			Screen screen2 = new Screen();
			Storage storage2 = new Storage();
			Memory memory2 = new Memory();
			Battery battery2 = new Battery();

			screen = smartphone1.getScreen();
			storage = smartphone1.getStorage();
			memory = smartphone1.getMemory();
			battery = smartphone1.getBattery();
			
			screen2 = smartphone2.getScreen();
			storage2 = smartphone2.getStorage();
			memory2 = smartphone2.getMemory();
			battery2 = smartphone2.getBattery();	*/		
			
	        if (smartphone1.getScreen().getSize() == smartphone2.getScreen().getSize() &&
	        		smartphone1.getStorage().getSize() == smartphone2.getStorage().getSize() &&
	        		smartphone1.getMemory().getSize() == smartphone2.getMemory().getSize() &&
	        		smartphone1.getBattery().getSize() == smartphone2.getBattery().getSize()) {
	            System.out.println("Equals");
	            return true;
	        } else {
	            System.out.println("Not Equals");
	            return false;
	        }
		}

		public Boolean batteryComp(Battery battery, Battery battery2) {			
	        if (battery.getSize() == battery2.getSize()) {
	            System.out.println("Equals Batteries");
	            return true;
	        } else {
	            System.out.println("Not Equals Batteries");
	            return false;
	        }
		}
		public Boolean memoryComp(Memory memory, Memory memory2) {			
	        if (memory.getSize() == memory2.getSize()) {
	            System.out.println("Equals Memory");
	            return true;
	        } else {
	            System.out.println("Not Equals Memory");
	            return false;
	        }
		}
		public Boolean screenComp(Screen screen, Screen screen2) {			
	        if (screen.getSize() == screen2.getSize()) {
	            System.out.println("Equals Screen");
	            return true;
	        } else {
	            System.out.println("Not Equals Screen");
	            return false;
	        }
		}
		public Boolean storageComp(Storage storage, Storage storage2) {			
	        if (storage.getSize() == storage2.getSize()) {
	            System.out.println("Equals Storage");
	            return true;
	        } else {
	            System.out.println("Not Equals Storage");
	            return false;
	        }
		}

		public Boolean itemComp(Item item, Item item2) {			
	        if (item.getItemID() == item2.getItemID()) {
	        	//item.getItemID() == item.getItemID()
	        	System.out.println(item.getClass());
	        	System.out.println(item2.getClass());
	            System.out.println("Equals Item");
	            return true;
	        } else {
	        	System.out.println(item.getClass().getName());
	        	System.out.println(item2.getClass());
	            System.out.println("Not Equals Item");
	            return false;
	        }
		}


}
