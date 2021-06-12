package agent;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.parser.ParseException;

import com.google.common.base.Strings;

import brown.communication.messages.ITradeMessage;
import brown.simulations.OfflineSimulation;
import brown.system.setup.library.Setup;
import brown.user.agent.IAgent;
import brown.user.agent.library.AbsSpectrumAuctionAgent;
import brown.user.agent.library.OnlineAgentBackend;
import brown.user.agent.library.Tier1SpectrumAuctionAgent;

public class MySpectrumAuctionAgent extends AbsSpectrumAuctionAgent implements IAgent {
	private final static String NAME = ""; // TODO: give your agent a name.

	private Map<String, Double> lastP;
	private Set<String> myGoods;

	public MySpectrumAuctionAgent(String name) {
		super(name);
		// TODO: fill this in (if necessary)
		this.lastP = new HashMap<>();
		this.myGoods = new HashSet<>();
	}

	@Override
	protected void onAuctionStart() {
		// TODO: fill this in (if necessary)
		this.lastP.clear();
		this.myGoods.clear();
	}

	@Override
	protected void onAuctionEnd(Map<Integer, Set<String>> allocations, Map<Integer, Double> payments,
			List<List<ITradeMessage>> tradeHistory) {
		// TODO: fill this in (if necessary)
		this.lastP.clear();
		this.myGoods.clear();
	}

	@Override
	protected Map<String, Double> getNationalBids(Map<String, Double> minBids) {
		// TODO: fill this in

		Map<String, Double> bids = new HashMap<>();

		if (this.lastP.isEmpty()) {
			this.myGoods.addAll(minBids.keySet());
			bids = new HashMap<>(minBids);
			for (String s : bids.keySet()) {
				bids.put(s, this.getValuation(s) / 2.0 + 1.25);
			}
		} else {
			Set<String> jumped = new HashSet<>();
			Set<String> ignore = new HashSet<>();
			for (String g : minBids.keySet()) {
				if (minBids.get(g) > 2.51) {
					jumped.add(g);
				}

				if (!this.myGoods.contains(g) || minBids.get(g) > 1.5 * this.getValuation(g)) {
					ignore.add(g);
				}
			}

			this.myGoods.removeAll(ignore);

			Set<String> o = new HashSet<>();
			for (String g : minBids.keySet()) {
				if (ignore.contains(g)) {
					continue;
				}

				if (jumped.contains(g)) {
					bids.put(g, minBids.get(g));
				} else {
					o.add(g);
				}
			}

			if (!o.isEmpty()) {
				List<String> l = new ArrayList<>(o);
				l.sort(new Comparator<String>() {

					@Override
					public int compare(String o1, String o2) {
						// TODO Auto-generated method stub
						return -Double.compare(getValuation(o1) - minBids.get(o1), getValuation(o2) - minBids.get(o2));
					}

				});

				for (int i = 0; i < l.size(); i++) {
					if (i == 0) {
						bids.put(l.get(i), this.clipBid(l.get(i),
								this.getValuation(l.get(i)) * (1.5 - 0.05 * ignore.size()), minBids));
					} else {
						bids.put(l.get(i), minBids.get(l.get(i)) + 2);
					}
				}
			}
		}

		return bids;
	}

	@Override
	protected Map<String, Double> getRegionalBids(Map<String, Double> minBids) {
		// TODO: fill this in

		Map<String, Double> bids = new HashMap<>();

		if (this.lastP.isEmpty()) {

			List<String> best4 = new ArrayList<>(minBids.keySet());
			best4.sort(new Comparator<String>() {

				@Override
				public int compare(String o1, String o2) {
					// TODO Auto-generated method stub
					double d1 = getValuation(o1);
					double d2 = getValuation(o2);
					if (o1.compareTo("M") < 0) {
						d1 *= 0.75;
					}
					if (o2.compareTo("M") < 0) {
						d2 *= 0.75;
					}
					return -Double.compare(d1, d2);
				}
			});

			this.myGoods.add(best4.get(0));
			this.myGoods.add(best4.get(1));
			this.myGoods.add(best4.get(2));
			this.myGoods.add(best4.get(3));
			
			for (String s : myGoods) {
				bids.put(s, this.getValuation(s) / 2.0 + 1.25);
			}
		} else {
			Set<String> jumped = new HashSet<>();
			Set<String> ignore = new HashSet<>();
			for (String g : this.myGoods) {
				if (minBids.get(g) > 2.51) {
					jumped.add(g);
				}

				if (!this.myGoods.contains(g) || minBids.get(g) > 1.3 * this.getValuation(g)) {
					ignore.add(g);
				}
			}

			this.myGoods.removeAll(ignore);

			Set<String> o = new HashSet<>();
			for (String g : this.myGoods) {
				if (ignore.contains(g)) {
					continue;
				}

				if (jumped.contains(g)) {
					bids.put(g, minBids.get(g));
				} else {
					o.add(g);
				}
			}

			if (!o.isEmpty()) {
				List<String> l = new ArrayList<>(o);
				l.sort(new Comparator<String>() {

					@Override
					public int compare(String o1, String o2) {
						// TODO Auto-generated method stub
						return -Double.compare(getValuation(o1) - minBids.get(o1), getValuation(o2) - minBids.get(o2));
					}

				});

				for (int i = 0; i < l.size(); i++) {
					if (i == 0) {
						bids.put(l.get(i), this.clipBid(l.get(i),
								this.getValuation(l.get(i)) * (1.3 - 0.025 * ignore.size()), minBids));
					} else {
						bids.put(l.get(i), minBids.get(l.get(i)) + 2);
					}
				}
			}
		}
		
		this.lastP = new HashMap<>(minBids);
		return bids;
	}

	public static void main(String[] args) throws InterruptedException {
		if (args.length == 0) {
			// Don't change this.
			new OfflineSimulation("offline_test_config.json", "input_configs/gsvm_smra_offline.json", "outfile", false)
					.run();
		} else {
			// Don't change this.
			MySpectrumAuctionAgent agent = new MySpectrumAuctionAgent(args[2]);
			agent.addAgentBackend(new OnlineAgentBackend(args[0], Integer.parseInt(args[1]), new Setup(), agent));
			while (true) {
			}
		}
	}

}
