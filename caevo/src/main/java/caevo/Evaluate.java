package caevo;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import caevo.tlink.EventEventLink;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;
import caevo.tlink.TimeTimeLink;
import caevo.util.SieveStats;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Evaluation functions for TLink classification.
 *
 * @author chambers
 */
public class Evaluate {
	public static final TLink.Type[] labels = { TLink.Type.BEFORE, TLink.Type.AFTER, TLink.Type.SIMULTANEOUS,
		TLink.Type.INCLUDES, TLink.Type.IS_INCLUDED, TLink.Type.VAGUE };
	
	public static final String[] devDocs = { 
		"APW19980227.0487.tml", 
		"CNN19980223.1130.0960.tml", 
		"NYT19980212.0019.tml",  
		"PRI19980216.2000.0170.tml", 
		"ed980111.1130.0089.tml" 
	};
	
	public static final String[] testDocs = { 
		"APW19980227.0489.tml",
		"APW19980227.0494.tml",
		"APW19980308.0201.tml",
		"APW19980418.0210.tml",
		"CNN19980126.1600.1104.tml",
		"CNN19980213.2130.0155.tml",
		"NYT19980402.0453.tml",
		"PRI19980115.2000.0186.tml",
		"PRI19980306.2000.1675.tml" 
	};
	
	/**
	 * Determines if the given TLink exists in the goldLinks, and its relation is equal
	 * to or compatible (invertible or a more general relation) with the gold link.
	 * @param guessed A single TLink between two events.
	 * @param goldLinks A list of gold tlinks.
	 * @return True if guessed appears as is or inverted in goldLinks.
	 */
	public static boolean isLinkCorrect(TLink guessed, List<TLink> goldLinks) {
		if( guessed == null || goldLinks == null || goldLinks.size() == 0 ) 
			return false;
		
		for( TLink gold : goldLinks ) {
			if( gold.compareToTLink(guessed) ) {
//				System.out.println("Match! guess=" + guessed + "\tgold=" + gold);
				return true;
			}
		}
	
		return false;
	}
	
	public static SieveDocuments getTrainSet(SieveDocuments docs) {
		SieveDocuments newdocs = new SieveDocuments();
		for( SieveDocument doc : docs.getDocuments() )
			if( !exists(doc.getDocname(), devDocs) && !exists(doc.getDocname(), testDocs) )
				newdocs.addDocument(doc);
		return newdocs;
	}

	public static SieveDocuments getDevSet(SieveDocuments docs) {
		SieveDocuments newdocs = new SieveDocuments();
		for( SieveDocument doc : docs.getDocuments() )
			if( exists(doc.getDocname(), devDocs) )
				newdocs.addDocument(doc);
		return newdocs;		
	}

	public static SieveDocuments getTestSet(SieveDocuments docs) {
		SieveDocuments newdocs = new SieveDocuments();
		for( SieveDocument doc : docs.getDocuments() )
			if( exists(doc.getDocname(), testDocs) )
				newdocs.addDocument(doc);
		return newdocs;		
	}

	private static boolean exists(String name, String[] names) {
		for( String nn : names )
			if( name.equals(nn) ) return true;
		return false;
	}
	
	/**
	 * This function makes sure that the two events in each TLink follow document order.
	 * If not, then we invert the order so event 1 is 2 and 2 is 1, and we also invert the ordering relation.
	 * @param docs The documents to normalize.
	 */
	public static void normalizeAllTlinksByTextOrder(SieveDocuments docs) {
		for( SieveDocument doc : docs.getDocuments() ) {
			List<TLink> removal = new ArrayList<TLink>();
			List<TLink> addition = new ArrayList<TLink>();
			
			for( TLink link : doc.getTlinks() ) {
//				System.out.println("normalizing " + link + " instance=" + link.getClass().toString());
				
				// Event-event links.
				if( link instanceof EventEventLink ) {
					TextEvent first = doc.getEventByEiid(link.getId1());
					TextEvent second = doc.getEventByEiid(link.getId2());
					if( first == null || second == null )
						System.out.println("WARNING: document " + doc.getDocname() + " has a link with a null event: " + link);
					else if( !first.isBeforeInText(second) ) {
						removal.add(link);
						TLink.Type invertedRelation = TLink.invertRelation(link.getRelation());
						TLink newlink = TLink.clone(link);
						newlink.setId1(link.getId2());						
						newlink.setId2(link.getId1());
						newlink.setRelation(invertedRelation);
						addition.add(newlink);
					}
				}
				
				// Time-Time links.
				else if( link instanceof TimeTimeLink ) {
					Timex first = doc.getTimexByTid(link.getId1());
					Timex second = doc.getTimexByTid(link.getId2());
					boolean flip = false;
					if( first == null || second == null )
						System.out.println("WARNING: document " + doc.getDocname() + " has a link with a null timex: " + link);
					else if( first.getDocumentFunction() == Timex.DocumentFunction.CREATION_TIME &&
							     second.getDocumentFunction() != Timex.DocumentFunction.CREATION_TIME) {
						flip = true;
					}
					else if( first.getDocumentFunction() != Timex.DocumentFunction.CREATION_TIME && 
							     second.getDocumentFunction() != Timex.DocumentFunction.CREATION_TIME &&
							     !first.isBeforeInText(second) ) {
						flip = true;
					}
					
					if( flip ) {
						removal.add(link);
						TLink.Type invertedRelation = TLink.invertRelation(link.getRelation());
						TLink newlink = TLink.clone(link);
						newlink.setId1(link.getId2());						
						newlink.setId2(link.getId1());
						newlink.setRelation(invertedRelation);
						addition.add(newlink);
					}
				}
				
				// Event-Time links.
				else if( link instanceof EventTimeLink ) {
					TextEvent event;
					Timex time;
					boolean flip = false;
					
					// event - time
					if( link.getId1().startsWith("e") ) {
						event = doc.getEventByEiid(link.getId1());
						time = doc.getTimexByTid(link.getId2());

						if( event == null || time == null )
							System.out.println("WARNING: document " + doc.getDocname() + " has a link with a null event or timex: " + link);

						else if( !time.isDCT() && 
								(time.getSid() < event.getSid() || 
										(time.getSid() == event.getSid() && time.getTokenOffset() < event.getIndex()) ))
							flip = true;						
					}
					// time - event
					else {
						event = doc.getEventByEiid(link.getId2());
						time = doc.getTimexByTid(link.getId1());

						if( event == null || time == null )
							System.out.println("WARNING: document " + doc.getDocname() + " has a link with a null event or timex: " + link);

						else if( time.isDCT() ) flip = true;
						else if( event.getSid() < time.getSid() ||
								(event.getSid() == time.getSid() && event.getIndex() < time.getTokenOffset()) )
							flip = true;
					}

					if( flip ) {
						removal.add(link);
						TLink.Type invertedRelation = TLink.invertRelation(link.getRelation());
						TLink newlink = TLink.clone(link);
						newlink.setId1(link.getId2());						
						newlink.setId2(link.getId1());
						newlink.setRelation(invertedRelation);
						addition.add(newlink);
					}
				}
				
				else {  //if( link instanceof TLink )
					System.out.println("WARNING: a sieve is producing generic TLink instances. These must be specific! Evaluation is now unreliable.");
				}
			}
			
			for( TLink link : removal )
				doc.removeTlink(link);
			for( TLink link : addition )
				doc.addTlink(link);
		}
	}
	
	private static String determineLinkType(TLink link) {
		if( link.getId1().startsWith("e") && link.getId2().startsWith("e") )
			return "EELink";
		if( (link.getId1().startsWith("e") && link.getId2().equals("t0")) ||
				(link.getId1().equals("t0") && link.getId2().startsWith("e")) )
			return "EDCTLink";
		if( (link.getId1().startsWith("e") && link.getId2().startsWith("t")) ||
				(link.getId1().startsWith("t") && link.getId2().startsWith("e")) )
			return "ETLink";
		if( link.getId1().startsWith("t") && link.getId2().startsWith("t") )
			return "TTLink";

		return "GeneralTLink"; 
				
		/*
		if( link.getId1().startsWith("e") && link.getId2().startsWith("e") ) {
			if( link.getDocument().getEventByEiid(link.getId1()).getSid() == link.getDocument().getEventByEiid(link.getId2()).getSid() )
				return "InterSentEELink";
			else
				return "IntraSentEELink";
		}
		if( (link.getId1().startsWith("e") && link.getId2().startsWith("t")) ||
				(link.getId1().startsWith("t") && link.getId2().startsWith("e")) ) {
			if( link.getDocument().getEventByEiid(link.getId1()).getSid() == link.getDocument().getEventByEiid(link.getId2()).getSid() )
				return "InterSentEELink";
			else
				return "IntraSentEELink";
		}
		*/
	}
	
	/**
	 * Full evaluation of guesses to gold links. This penalizes guesses for not labeling everything.
	 * The goldDocs and guessedDocs should cover the same docs.
	 * @param goldDocs Gold tlinks in every document.
	 * @param guessedDocs The guessed tlinks in every document.
	 * @param sieveNames The list of sieve names, in order that they were applied to each document.
	 * @param sieveStats A map from sieve names to their SieveStats objects.
	 */
	public static void evaluate(SieveDocuments goldDocs, SieveDocuments guessedDocs, String[] sieveNames, Map<String,SieveStats> sieveStats) {
		Counter<String> guessCounts = new ClassicCounter<String>();
		Counter<TLink.Type> goldLabelCounts = new ClassicCounter<TLink.Type>();
		Counter<String> breakdownNumCorrect = new ClassicCounter<String>();
		Counter<String> breakdownNumIncorrect = new ClassicCounter<String>();
		int numCorrect = 0;
		int numCorrectNonVague = 0;
		int numIncorrect = 0;
		int numIncorrectNonVague = 0;
		int numMissed = 0;
		int numMissedNonVague = 0;

		if (goldDocs == null)
			return;
		
		// Make sure all TLinks follow text order and invert relations that don't.
//		normalizeAllTlinksByTextOrder(goldDocs);
		normalizeAllTlinksByTextOrder(guessedDocs);
		
		// Loop over documents.
		for( SieveDocument guessedDoc : guessedDocs.getDocuments() ) {
			SieveDocument goldDoc = goldDocs.getDocument(guessedDoc.getDocname());
			Set<String> seenGoldLinks = new HashSet<String>();

//			System.out.println("evaluating " + guessedDoc.getDocname());
//			System.out.println("\t-> " + guessedDoc.getTlinks().size() + " guessed links with " + goldDoc.getTlinks().size() + " gold links.");

			// Gold links.
			List<TLink> goldLinks = goldDoc.getTlinksNoClosures();
			Map<String, TLink> goldPairLookup = new HashMap<String, TLink>();
			for (TLink tlink : goldLinks) {
				goldPairLookup.put(tlink.getId1() + "," + tlink.getId2(), tlink);
				goldLabelCounts.incrementCount(tlink.getRelation());
			}
			
			// Run it.
			List<TLink> proposed = guessedDoc.getTlinks();
//			System.out.println("EVALUATE: proposed links in " + guessedDoc.getDocname() + " = " + proposed);

			// Check proposed links.
			for( TLink pp : proposed ) {
				TLink goldLink = goldPairLookup.get(pp.getId1() + "," + pp.getId2());

				if( goldLink != null ) {
					guessCounts.incrementCount(goldLink.getRelation()+" "+pp.getRelation());
					seenGoldLinks.add(pp.getId1() + "," + pp.getId2());
				}

				// Guessed link is correct!
				if( Evaluate.isLinkCorrect(pp, goldLinks) ) {
					numCorrect++;
					breakdownNumCorrect.incrementCount(pp.isFromClosure() ? "closed" : "notclosed");
					breakdownNumCorrect.incrementCount(determineLinkType(pp));
					if (!pp.getRelation().equals(TLink.Type.VAGUE)) {
						numCorrectNonVague++;
					}
					if( pp.getOrigin() != null ) {
						sieveStats.get(pp.getOrigin()).addCorrect(pp);
					}
					else System.out.println("EVALUATE: unknown link origin: " + pp);
				} 
				// Gold and guessed link disagree!
				// Only mark relations wrong if there's a conflicting human annotation.
				// (if there's no human annotation, we don't know if it's right or wrong)
				else if (goldLink != null) {
					numIncorrect++;
					breakdownNumIncorrect.incrementCount(pp.isFromClosure() ? "closed" : "notclosed");
					breakdownNumIncorrect.incrementCount(determineLinkType(pp));
					if (!goldLink.getRelation().equals(TLink.Type.VAGUE)) {
						numIncorrectNonVague++;
					}
					if( pp.getOrigin() != null ) sieveStats.get(pp.getOrigin()).addIncorrect(pp, goldLink);
					else System.out.println("EVALUATE: unknown link origin: " + pp);
				}
				// No gold link. We don't penalize for guessed links that aren't in gold.
				else {
					sieveStats.get(pp.getOrigin()).addNoGold(pp);
//					System.out.println("No gold link: " + pp);
				}
			}
			
			// Check for gold links that were not predicted. Penalize for them being missed.
			for( TLink gold : goldLinks ) {
				if( !seenGoldLinks.contains(gold.getId1() + "," + gold.getId2()) ) {
					numMissed++;
					if (!gold.getRelation().equals(TLink.Type.VAGUE)) {
						numMissedNonVague++;
					}
//					System.out.println("Unlabeled gold: " + guessedDoc.getDocname() + " " + gold);
				}
			}
		}
		
		// Print performance for each individual sieve.
		System.out.println("\nBrief Sieve Stats");
		for( String sieveName : sieveNames )
			sieveStats.get(sieveName).printOneLineStats();
		System.out.println("\nDetailed Sieve Stats");
		for( String sieveName : sieveNames )
			sieveStats.get(sieveName).printStats();
		for( String sieveName : sieveNames )
			sieveStats.get(sieveName).dumpStatsToFile();

		// Calculate precision and output the sorted sieves.
		int totalGuessed = numCorrect + numIncorrect;
		int totalGold = numCorrect + numIncorrect + numMissed;
		double precision = (totalGuessed > 0 ? (double)numCorrect / totalGuessed : 0.0);
		double recall = (totalGold > 0 ? (double)numCorrect / totalGold : 0.0);
		double f1 = (precision+recall > 0 ? 2.0 * precision * recall / (precision+recall) : 0.0);
		int totalGuessedNonVague = numCorrectNonVague + numIncorrectNonVague;
		int totalGoldNonVague = numCorrectNonVague + numIncorrectNonVague + numMissedNonVague;
		double precisionNonVague = (totalGuessedNonVague > 0 ? (double)numCorrectNonVague / (double)totalGuessedNonVague : 0.0);
		double recallNonVague = (totalGuessedNonVague > 0 ? (double)numCorrectNonVague / (double)totalGoldNonVague : 0.0);
		double f1NonVague = (precisionNonVague + recallNonVague > 0 ? 2.0 * precisionNonVague * recallNonVague / (precisionNonVague + recallNonVague) : 0.0);

		// Print full system performance.
		System.out.println("\n*********************************************************************");
		System.out.println("************************** FULL RESULTS *****************************");
		System.out.println("*********************************************************************");
		System.out.printf(
				"precision\t= %.3f\t %d of %d\n" +
				"recall   \t= %.3f\t %d of %d\n" +
				"F1       \t= %.3f\n" +
				"precision (non-VAGUE)\t= %.3f\t %d of %d\n" +
				"recall (non-VAGUE)   \t= %.3f\t %d of %d\n" +
				"F1 (non-VAGUE)       \t= %.3f\n",
				precision, numCorrect, totalGuessed,
				recall, numCorrect, totalGold,
				f1,
				precisionNonVague, numCorrectNonVague, totalGuessedNonVague,
				recallNonVague, numCorrectNonVague, totalGoldNonVague,
				f1NonVague);
		System.out.println();

		System.out.printf("Links directly from sieves: %.0f correct\t%.0f incorrect\t P=%.3f\n", breakdownNumCorrect.getCount("notclosed"), breakdownNumIncorrect.getCount("notclosed"),
				breakdownNumCorrect.getCount("notclosed")/(breakdownNumCorrect.getCount("notclosed")+breakdownNumIncorrect.getCount("notclosed")));
		System.out.printf("Links from transitivity:    %.0f correct\t%.0f incorrect\t P=%.3f\n", breakdownNumCorrect.getCount("closed"), breakdownNumIncorrect.getCount("closed"),
				breakdownNumCorrect.getCount("closed")/(breakdownNumCorrect.getCount("closed")+breakdownNumIncorrect.getCount("closed")));

		for( String key : breakdownNumCorrect.keySet() )
			System.out.printf("Links from %s:\ttotal=%.0f\tP=%.0f/%.0f = %.3f\tR=%.0f/%d = %.3f\n", key, breakdownNumCorrect.getCount(key) + breakdownNumIncorrect.getCount(key),
					breakdownNumCorrect.getCount(key), breakdownNumCorrect.getCount(key) + breakdownNumIncorrect.getCount(key),
					breakdownNumCorrect.getCount(key)/(breakdownNumCorrect.getCount(key)+breakdownNumIncorrect.getCount(key)),
					breakdownNumCorrect.getCount(key), totalGold, breakdownNumCorrect.getCount(key)/totalGold
					);

		
		printBaseline(goldLabelCounts);
		printDatasetStats(goldLabelCounts);
		printConfusionMatrix(guessCounts);
		printPerRelationPRF(guessCounts, goldLabelCounts);
		System.out.println("*********************************************************************\n");
	}
	
	
	/**
	 * Find the label with the most counts, and print the baseline if you always guessed that one.
	 */
	public static void printBaseline(Counter<TLink.Type> labelCounts) {
		printBaseline(labelCounts, System.out);
	}
	public static void printBaseline(Counter<TLink.Type> labelCounts, PrintStream printer) {
		double total = labelCounts.totalCount();
		TLink.Type best = null;
		double bestc = -1.0;
		for( TLink.Type label : labelCounts.keySet() ) {
			if( labelCounts.getCount(label) > bestc ) {
				bestc = labelCounts.getCount(label);
				best = label;
			}
		}
		printer.printf("Local Baseline (%s): precision = recall = F1 = %.3f\n", best, (bestc/total));		
	}

	/**
	 * Print how many times each label is in the counts list.
	 */
	public static void printDatasetStats(Counter<TLink.Type> labelCounts) {
		printDatasetStats(labelCounts, System.out);
	}
	public static void printDatasetStats(Counter<TLink.Type> labelCounts, PrintStream printer) {
		printer.println("GOLD LABEL COUNTS (out of " + (int)labelCounts.totalCount() + ")");
		for( TLink.Type label : labels ) {
			int numtabs = (label.toString().length() > 7 ? 1 : 2);
			printer.print(label + "\t");
			if( numtabs == 2 ) printer.print("\t");
			printer.printf("%d\t%.0f%%\n", (int)labelCounts.getCount(label), (100.0*labelCounts.getCount(label)/labelCounts.totalCount()));
		}
	}
	
	/**
	 * Print the confusion matrix for the 6 label types. Each String key should be a pair separated
	 * by a single space, representing a guess for a gold label: e.g., "before after" 
	 * @param guessCounts Number of times each guessed label was made for each gold label.
	 */
	public static void printConfusionMatrix(Counter<String> guessCounts) {
		printConfusionMatrix(guessCounts, System.out);
	}
	public static void printConfusionMatrix(Counter<String> guessCounts, PrintStream printer) {
		for( TLink.Type label2 : labels )
			printer.print("\t" + label2.toString().substring(0,Math.min(label2.toString().length(), 6)));
		printer.println("\t(guesses)");
		
		for( TLink.Type label1 : labels ) {
			printer.print(label1.toString().substring(0, Math.min(label1.toString().length(), 6)) + "\t");
			
			for( TLink.Type label2 : labels ) {
				if( guessCounts.containsKey(label1+" "+label2) )
					printer.print((int)guessCounts.getCount(label1+" "+label2) + "\t");
				else printer.print("0\t");
			}
			printer.println();
		}
	}
	
	/**
	 * Print the confusion matrix for the 6 label types. Each String key should be a pair separated
	 * by a single space, representing a guess for a gold label: e.g., "before after" 
	 * @param guessCounts Number of times each guessed label was made for each gold label.
	 */
	public static void printPerRelationPRF(Counter<String> guessCounts, Counter<TLink.Type> goldLabelCounts) {
		printPerRelationPRF(guessCounts, goldLabelCounts, System.out);
	}
	public static void printPerRelationPRF(Counter<String> guessCounts, Counter<TLink.Type> goldLabelCounts, PrintStream printer) {
		printer.println("-------------------------------\nPer Relation P/R/F1");
		for( TLink.Type label1 : labels ) {
			printer.print(label1.toString().substring(0, Math.min(label1.toString().length(), 6)) + "\t");
			
			double correct = guessCounts.getCount(label1+" "+label1);
			double totalPossible = goldLabelCounts.getCount(label1);
			double totalGuessed  = 0.0;
			
//			for( TLink.Type label2 : labels ) {
//				if( guessCounts.containsKey(label1+" "+label2) )
//					totalPossible += guessCounts.getCount(label1+" "+label2);
//			}

			for( TLink.Type label2 : labels ) {
				if( guessCounts.containsKey(label2+" "+label1) )
					totalGuessed += guessCounts.getCount(label2+" "+label1);
			}

			double precision = correct / totalGuessed;
			double recall    = correct / totalPossible;
			double f1 			 = (precision + recall > 0 ? 2.0 * precision * recall / (precision + recall) : 0.0);
			printer.println(String.format("p=%.2f\tr=%.2f\tf1=%.2f\tcorrect=%.0f\tguessed=%.0f\tpossible=%.0f", precision, recall, f1, correct, totalGuessed, totalPossible));
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
	}

}
