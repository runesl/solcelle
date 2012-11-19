import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

public class LoadProduction {
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss");
    private static SimpleDateFormat sdf2 = new SimpleDateFormat("dd-MM-yyyyHH");

    public static int[] consumptionMonthWeightsEnfamiliehus = {10,9,9,8,7,6,6,7,8,9,10,11};

    public static final int SELF_CONSUMPTION_WATTS = 822;

    public static void main(String[] args) throws IOException, ParseException {
        Map<Date, ProductionRecord> productionRecordMap = loadProduction();
        Map<Date, PriceRecord> prices = loadPrices2011();

        // Calculate value of sold production
        double producedKwh = 0, soldKwh = 0, priceForSold = 0;
        for (ProductionRecord productionRecord : productionRecordMap.values()) {
            PriceRecord priceRecord = prices.get(new Date(3600000 * (productionRecord.date.getTime() / 1000 / 3600)));
            producedKwh += productionRecord.getProducedKwh();
            soldKwh += productionRecord.getSoldKwh();
            priceForSold += productionRecord.getSoldKwh() * priceRecord.dkkPrKwh;
        }
        System.out.println("Total production kWh: " + producedKwh + ". Production sold (above " + SELF_CONSUMPTION_WATTS + "W base consumption - weighted after months) kWh: " + soldKwh + " Total price for sold DKK: " + priceForSold + " avg. price DKK/kWh: " + priceForSold / soldKwh);

        // Calculate value of bought power.
        double totalBoughtDkk = 0, totalBoughtkWh = 0, selfConsumption = 0;
        for (PriceRecord priceRecord : prices.values()) {
            double producedKwHInHour = 0;
            ProductionRecord productionRecord = productionRecordMap.get(priceRecord.date);
            if (productionRecord != null)
                producedKwHInHour = productionRecord.power / 1000.0;
            double selfConsumptionWatts = getSelfConsumptionWattsForTime(priceRecord.date);
            if (producedKwHInHour > selfConsumptionWatts / 1000.0) {
                selfConsumption += selfConsumptionWatts / 1000.0;
                continue; // No buy, if were making enough
            }
            double boughtInHour = (selfConsumptionWatts / 1000.0) - producedKwHInHour;
            selfConsumption += producedKwHInHour;
            totalBoughtkWh += boughtInHour;
            totalBoughtDkk += priceRecord.dkkPrKwh * boughtInHour;
        }
        System.out.println("Total bought kWh: " + totalBoughtkWh + " Total price of bought power DKK: " + totalBoughtDkk + "Avg. price DKK/kWh: " + totalBoughtDkk / totalBoughtkWh);
        double totalConsumption = 365 * 24 * SELF_CONSUMPTION_WATTS / 1000.0;
        System.out.println("Total power consumption kWh: " + totalConsumption + ". Total consumption of solar power generated in same hour kWh: " + selfConsumption + ". Percentage of total power usage: " + 100 * selfConsumption / totalConsumption);
    }

    private static double getSelfConsumptionWattsForTime(Date time){
        return 1.0 * consumptionMonthWeightsEnfamiliehus[time.getMonth()] / 100*12 * SELF_CONSUMPTION_WATTS;
    }

    private static Map<Date, PriceRecord> loadPrices2011() throws IOException, ParseException {
        Map<Date, PriceRecord> prices = new TreeMap<Date, PriceRecord>();
        File in = new File("spot2011.txt");
        BufferedReader br = new BufferedReader(new FileReader(in));
        for (String read = br.readLine(); read != null; read = br.readLine()) {
            String[] tokens = read.split("\t");
            Date date = sdf2.parse(tokens[0] + tokens[1]);
            double dkkPrKwh = Double.parseDouble(tokens[2].replace(",", ".")) / 1000.0;
            prices.put(date, new PriceRecord(dkkPrKwh, date));
        }
        System.out.println("Read " + prices.size() + " prices");
        return prices;
    }

    private static Map<Date, PriceRecord> loadPrices2010() throws IOException, ParseException {
        Map<Date, PriceRecord> prices = new TreeMap<Date, PriceRecord>();
        File in = new File("spot2010.txt");
        BufferedReader br = new BufferedReader(new FileReader(in));
        for (String read = br.readLine(); read != null; read = br.readLine()) {
            String[] tokens = read.split("\t");
            Date date = sdf2.parse(tokens[0] + tokens[1]);
            date.setYear(date.getYear()+1); // Pretend to be data for 2011 in order to correlate with production data.
            double dkkPrKwh = Double.parseDouble(tokens[2].replace(",", ".")) / 1000.0;
            prices.put(date, new PriceRecord(dkkPrKwh, date));
        }
        System.out.println("Read " + prices.size() + " prices");
        return prices;
    }

    private static Map<Date, ProductionRecord> loadProduction() throws IOException, ParseException {
        Map<Date, ProductionRecord> records = new TreeMap<Date, ProductionRecord>();
        File in = new File("produceret_2011.txt");
        BufferedReader br = new BufferedReader(new FileReader(in));
        br.readLine();
        for (String read = br.readLine(); read != null; read = br.readLine()) {
            String[] tokens = read.split("\t");
            if (tokens.length < 2 || "".equals(tokens[1])) continue;
            String dayTime = tokens[0];
            Date date = sdf.parse(dayTime);
            String power = tokens[1];
            records.put(date, new ProductionRecord(Integer.parseInt(power), date));
        }

        double totalKwh = 0;
        for (ProductionRecord productionRecord : records.values()) {
            totalKwh += productionRecord.getProducedKwh();
        }
        System.out.println("Read " + records.size() + " production records. Total production: " + totalKwh);
        return records;
    }

    public static class ProductionRecord {
        int power;
        Date date;

        public ProductionRecord(int power, Date date) {
            this.power = power;
            this.date = date;
        }

        public double getSoldKwh() {
            double selfConsumptionWatts = getSelfConsumptionWattsForTime(date);
            if (power < selfConsumptionWatts) return 0;
            return 1.0 * (power - selfConsumptionWatts) / 1000.0 / 6;
        }

        public double getProducedKwh() {
            return 1.0 * power / 1000.0 / 6;
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
