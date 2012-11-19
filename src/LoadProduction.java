import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

public class LoadProduction {
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss");
    static SimpleDateFormat sdf2 = new SimpleDateFormat("dd-MM-yyyyHH");
    static Map<Date, ProductionRecord> tenMins = new TreeMap<Date, ProductionRecord>();
    static Map<Date, PriceRecord> prices = new TreeMap<Date, PriceRecord>();
    public static final int SELF_CONSUMPTION = 822;

    public static void main(String[] args) throws IOException, ParseException {
        File in = new File("produceret_2011.txt");
        BufferedReader br = new BufferedReader(new FileReader(in));
        br.readLine();
        for (String read = br.readLine(); read != null ; read=br.readLine()){
            String [] tokens = read.split("\t");
            if (tokens.length <2 || "".equals(tokens[1])) continue;
            String dayTime = tokens[0];
            Date date = sdf.parse(dayTime);
            String power = tokens[1];
            tenMins.put(date, new ProductionRecord(Integer.parseInt(power), date));
        }

        double totalKwh = 0;
        for (ProductionRecord productionRecord : tenMins.values()) {
            totalKwh+= productionRecord.getProducedKwh();
        }
        System.out.println("Read " + tenMins.size() + " production records. Total production: "+totalKwh);

        in = new File("Elspot Prices_2011_Hourly_DKK.txt");
        br = new BufferedReader(new FileReader(in));
        for (String read = br.readLine(); read != null ; read=br.readLine()){
            String [] tokens = read.split("\t");
            Date date = sdf2.parse(tokens[0] + tokens[1]);
            double dkkPrKwh = Double.parseDouble(tokens[2].replace(",", "."))/1000.0;
            prices.put(date, new PriceRecord(dkkPrKwh, date));
        }
        System.out.println("Read " + prices.size() + " prices");

        double producedKwh=0, soldKwh=0, priceForSold=0;
        for (ProductionRecord productionRecord : tenMins.values()) {
            PriceRecord priceRecord = prices.get(new Date(3600000 * (productionRecord.date.getTime() / 1000 / 3600)));
            producedKwh+=productionRecord.getProducedKwh();
            soldKwh+=productionRecord.getSoldKwh();
            priceForSold+=productionRecord.getSoldKwh()*priceRecord.dkkPrKwh;
        }
        System.out.println("Total production kWh: " + producedKwh + ". Production sold (above " + SELF_CONSUMPTION + "W base consumption) kWh: " + soldKwh + " Total price for sold DKK: " + priceForSold + " avg. price DKK/kWh: " + priceForSold/soldKwh);

        double sum=0;
        double totalBoughtkWh=0;
        int hoursWhereRebuyIsNecesary = 0;
        double selfConsumption = 0;
        for (PriceRecord priceRecord : prices.values()) {
            double producedKwHInHour = 0;
            ProductionRecord productionRecord = tenMins.get(priceRecord.date);
            if (productionRecord != null)
                producedKwHInHour = productionRecord.power / 1000.0;
            if (producedKwHInHour > SELF_CONSUMPTION / 1000.0){
                selfConsumption+=SELF_CONSUMPTION/1000.0;
                continue; // No rebuy, if were making enough
            }
            double reboughtInHour = (SELF_CONSUMPTION / 1000.0) - producedKwHInHour;
            selfConsumption+=producedKwHInHour;
            totalBoughtkWh += reboughtInHour;
            sum+=priceRecord.dkkPrKwh*reboughtInHour;
        }
        System.out.println("Total bought kWh: " + totalBoughtkWh + " Total price of bought power DKK: " + sum + "Avg. price DKK/kWh: " + sum/totalBoughtkWh);
        double totalConsumption = 365 * 24 * SELF_CONSUMPTION / 1000.0;
        System.out.println("Total power consumption kWh: " + totalConsumption + ". Total consumption of solar power generated in same hour kWh: " + selfConsumption + ". Percentage of total power usage: " + 100*selfConsumption/ totalConsumption);
    }

    public static class ProductionRecord {
        int power;
        Date date;

        public ProductionRecord(int power, Date date) {
            this.power = power;
            this.date = date;
        }

        public double getSoldKwh(){
            if (power < SELF_CONSUMPTION) return 0;
            return 1.0*(power-SELF_CONSUMPTION)/1000.0/6;
        }

        public double getProducedKwh(){
            return 1.0*power/1000.0/6;
        }

    }

    public static class PriceRecord {
        double dkkPrKwh;
        Date date;

        public PriceRecord(double dkkPrKwh, Date date) {
            this.dkkPrKwh = dkkPrKwh;
            this.date = date;
        }

    }



}
