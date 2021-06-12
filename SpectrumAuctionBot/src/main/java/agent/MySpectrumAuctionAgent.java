package agent;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import brown.auction.value.valuation.IGeneralValuation;
import brown.platform.item.ICart;
import brown.platform.item.IItem;
import brown.platform.item.library.Cart;
import brown.platform.item.library.Item;
import brown.user.agent.library.localbid.BidVector;
import brown.user.agent.library.localbid.IBidVector;
import brown.user.agent.library.localbid.ILinearPrices;
import brown.user.agent.library.localbid.LinearPrices;
import org.json.simple.parser.ParseException;

import brown.communication.messages.ITradeMessage;
import brown.simulations.OfflineSimulation;
import brown.system.setup.library.Setup;
import brown.user.agent.IAgent;
import brown.user.agent.library.AbsSpectrumAuctionAgent;
import brown.user.agent.library.OnlineAgentBackend;
import brown.user.agent.library.Tier1SpectrumAuctionAgent;
import org.spectrumauctions.sats.core.model.lsvm.LSVMBidder;

public class MySpectrumAuctionAgent extends AbsSpectrumAuctionAgent implements IAgent {
    private final static String NAME = "Luckybot"; // TODO: give your agent a name.

    private HashMap<String, Double> lastP;
    private Set<String> myGoods;
    private double epsilon;

    public MySpectrumAuctionAgent(String name) {
        super(name);
        this.lastP = new HashMap<>();
        this.myGoods = new HashSet<>();
        this.epsilon = 1.25;
    }

    @Override
    protected void onAuctionStart() {
        this.lastP.clear();
        this.myGoods.clear();
    }

    @Override
    protected void onAuctionEnd(Map<Integer, Set<String>> allocations, Map<Integer, Double> payments,
                                List<List<ITradeMessage>> tradeHistory) {
        this.lastP.clear();
        this.myGoods.clear();
    }

    /**
     * A helper that bid the maximum between bid and minBids
     * @param good - a string representing the good to bid on
     * @param bid - a double representing the bid
     * @param minBids - a map representing the bids of this round
     * @return a double representing the bid for this round
     */
    protected double clipBid2(String good, double bid, Map<String, Double> minBids) {
        return Math.max(bid, minBids.getOrDefault(good, 0.0));
    }


    @Override
    protected Map<String, Double> getNationalBids(Map<String, Double> minBids) {
        // lastP denotes the current prices
        // set last price be minBids
        for (String s : minBids.keySet()) {
            this.lastP.put(s, minBids.get(s));
        }

        HashMap<String, Double> bids;
        bids = localBid(minBids.keySet(),new HashMap<>(minBids),10);

        //set ignore. ignore those whose "bid" is less than minBids
        Set<String> ignore = new HashSet<>();
        for (String g : minBids.keySet()) {
            if (minBids.get(g) > bids.get(g)) {
                ignore.add(g);
            }
        }

        // remove the ignored items from my bids
        for(String item: ignore) {
            bids.remove(item);
        }

        // Check if the national bids submitted are valid
        System.out.println("valid check for national bids");
        boolean validChecker = isValidBidBundle(bids, minBids, true);
        System.out.println(validChecker);
        return bids;
    }


    @Override
    protected Map<String, Double> getRegionalBids(Map<String, Double> minBids) {

        HashMap<String, Double> bids;
        for (String s : minBids.keySet()) {
            this.lastP.put(s, minBids.get(s));
        }

        List<String> best4 = new ArrayList<>(minBids.keySet());
        bids = localBid(minBids.keySet(),new HashMap<>(minBids),10);
        best4.sort(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                // TODO Auto-generated method stub
                double d1 = getValuation(o1);
                double d2 = getValuation(o2);
                return -Double.compare(d1, d2);
            }
        });

        HashMap<String, Double> finalBids = new HashMap<>();
        for (String s : best4) {
            if  (finalBids.size()<4) {
                if (bids.get(s) >= minBids.get(s)) {
                    finalBids.put(s, clipBid2(s,bids.get(s),minBids)+0.01);
                }
            }
        }
        System.out.println("validChecker for regional bidders");
        boolean validChecker = isValidBidBundle(finalBids, minBids, true);
        System.out.println(validChecker);
        return finalBids;
    }

    /**
     * populate a hashMap of bids for each good
     * @param G - a set of goods represented by strings
     * @param p - a Hashmap representing the price for each good
     * @param numIterations - number of iterations
     * @return a hashmap of bids for each good
     */

    public  HashMap<String, Double> localBid(Set<String> G, HashMap<String, Double> p, int numIterations) {
        HashMap<String, Double> bInit = new HashMap<>();

        Random rand = new Random();
        double alpha = 0.05 * rand.nextDouble();

        // populate bInit with a bid for each good in G.bInit.setBid(IItem, double).
        for (String good: G){
            bInit.put(good,this.getValuation(good));
        }

        for (int i = 0; i < numIterations; i++) {
            HashMap<String, Double> b = bInit;

            for (String gi : G) {
                Set<String> GCopy = new HashSet<>(G);
                double mv = calcExpectedMarginalValue(GCopy, gi, b, p);
                b.put(gi,mv);
            }

            // update bInit
            for (String good : G) {
                double bInitUpdate = alpha * (bInit.get(good)) + (1 - alpha) * b.get(good);
                bInit.put(good, bInitUpdate);
            }
        }

        return bInit;
    }

    /**
     * calculate the expected marginal value of good
     * @param G - a set of goods represented by strings
     * @param good - a good represented by a string
     * @param b - a hashmap of bids
     * @param p - a hashmap of prices
     * @return a double representing the expected marginal value of the good
     */
    public  double calcExpectedMarginalValue(Set<String> G, String good, HashMap<String,Double> b, HashMap<String, Double> p) {
        double totalMV;
        HashSet<String> bundle = new HashSet<>(this.getTentativeAllocation());
        G.remove(good);
        for (String item : G) {
            double itemPrice = p.get(item);
            double itemBid = b.get(item);
            if (itemBid > itemPrice) {
                bundle.add(item);
            }
        }
        G.add(good);
        bundle.add(good);
        double v1 = this.getValuation(bundle);
        bundle.remove(good);
        double v2 = this.getValuation(bundle);
        totalMV = (v1 - v2);
        return totalMV;
    }


    public static void main(String[] args) throws InterruptedException {
        if (args.length == 0) {
            // Don't change this.
            new OfflineSimulation("offline_test_config.json", "input_configs/gsvm_smra_offline.json", "outfile", false).run();
        } else {
            // Don't change this.
            Tier1SpectrumAuctionAgent agent = new Tier1SpectrumAuctionAgent(args[2]);
            agent.addAgentBackend(new OnlineAgentBackend(args[0], Integer.parseInt(args[1]), new Setup(), agent));
            while (true) {
            }
        }
    }

}
