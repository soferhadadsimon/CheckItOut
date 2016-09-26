package Model.Data;

import java.util.ArrayList;

import Model.Item.Item;

public class QrInterpreter {

    /**
     * get the items from qr string representation.
     * returns null in case of failure
     *
     * @param qrString
     * @return {ArrayList<Item>|null} - list of items
     */
    public static ArrayList<Item> getItems(String qrString) {
        try {
            ArrayList<Item> items = new ArrayList<>();
            String[] itemsStrings = qrString.split(";");
            for (String itemStr : itemsStrings) {
                String[] itemProperties = itemStr.split(",");

                String name = itemProperties[0];
                String price = itemProperties[1];
                String amount = itemProperties[2];

                Item item = new Item(name, Double.parseDouble(price), Integer.parseInt(amount));
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            System.err.println("QrInterpreter error: " + e.getMessage());
            return null;
        }
    }
}
