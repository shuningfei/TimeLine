package caevo.annotate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;




import org.xml.sax.SAXException;

import caevo.Closure;
import caevo.tlink.EventEventLink;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;
import caevo.tlink.TimeTimeLink;

/**
 * Based on Annotator class. Makes necessary adjustments to add "undo" function.
 * Only one "undo" is supported. Better implementation welcome; maybe turn pairsJustLabeled into a stack?
 * 
 * Reads an HTML file from the TimeBank online browser. Prints all pairs of events/times that
 * occur in the same or next sentence, and lets the user type in a relation for each one. It
 * prompts for a pair one at a time. The user can save progress to a text file and load it
 * later to pick up where he/she left off.
 * 
 * Annotator <html-file> [progress-file]
 * 
 * @author cassidy
 */
public class Annotator2 {
  Map<String,TLink> allLinks;  // TLink object for each pair (labeled and/or unlabeled)
  List<TLink> labeledRelations; // list of only labeled TLinks (to feed to closure computation)
  Set<String> allLinksHash;  // String for each pair. Helps when closure computation uses different event/time ordering.
  Set<String> pairsJustLabeled; // set of pairs that were just labeled (to support "undo" option)
	Closure closure; // closure computing object
	int numRelations; // the number of total relations to label in the document
	int curKey; // pair that is currently set to be annotated
  
	public Annotator2() throws IOException {
		labeledRelations = new ArrayList<TLink>();
    closure = new Closure();
    numRelations = 0;
    pairsJustLabeled = new HashSet<String>();
	}
	
	class OrderComparator implements Comparator<Node>{
		public int compare(Node node1, Node node2) {
			int startOffset1;
			int startOffset2;
			
			// get start offset from each event
			int offset1 = getStartOffset(node1);
			int offset2 = getStartOffset(node2);
			return offset2 - offset1;
		}
		
		public int getStartOffset(Node node){
			if (node.getNodeName().toString().equals("event_mention")) {
				return getEventStartOffset(node);
			}
			else if (node.getNodeName().toString().equals("timex2_mention")) {
				return getTimex2StartOffset(node);
			}
			else return getEventStartOffset(node);
		}
		private int getTimex2StartOffset(Node timex2) {
			int offset;
			
			Node extent = timex2.getFirstChild();
			Node charseq = extent.getFirstChild();
			String startString = charseq.getAttributes().getNamedItem("START").toString();
			offset = Integer.parseInt(startString);
			
			return offset;
		}

		public int getEventStartOffset(Node event) {
			
			int offset;
			
			Node extent = event.getFirstChild();
			Node charseq = extent.getFirstChild();
			String startString = charseq.getAttributes().getNamedItem("START").toString();
			offset = Integer.parseInt(startString);
			
			return offset;
		}
	}
	
	public Document readXML_ACE2005(String path) throws SAXException, IOException {
		File aceFile = new File(path);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(aceFile);
			doc.getDocumentElement().normalize();
			return doc;
		}
		catch (ParserConfigurationException e) {
			e.printStackTrace();
			return null;
		}
		
	}
	
	public void processXML_ACE2005(Document doc) {
		NodeList eventNodes = doc.getElementsByTagName("event_mention");
		List<Node> eventsList = new ArrayList<Node>();
		for (int i = 0; i < eventNodes.getLength(); i++) {
			eventsList.add(eventNodes.item(i));
		} 
		NodeList timex2Nodes = doc.getElementsByTagName("timex2_mention");
		List<Node> timex2List = new ArrayList<Node>();
		for (int i = 0; i < timex2Nodes.getLength(); i++) {
			timex2List.add(timex2Nodes.item(i));
		} 
		// sort events and times by START offset
		Collections.sort(eventsList, new OrderComparator());
		Collections.sort(timex2List, new OrderComparator());
		// create all pairs
		allLinks = new LinkedHashMap<String, TLink>();
		allLinksHash = new HashSet<String>();
		String keypair;
		String timex2Id;
		String eventId;
		// create DCT links for each event and timex2
		for (Node timex2 : timex2List) {
			String[] timex2IdElements = timex2.getAttributes().getNamedItem("ID").toString().split("-");
			timex2Id = timex2IdElements[1] + timex2IdElements[2];
			
			TLink dctlink = new EventEventLink(timex2Id, "t0", TLink.Type.NONE);
			keypair = dctlink.getId1() + " t0";
			allLinks.put(keypair, dctlink);
			allLinksHash.add(keypair);
			numRelations++;
		}
		
	}
	
	public void readHTML(String path) {
		String text = "";
		try {
			BufferedReader reader = new BufferedReader(new FileReader(path));
			String line;
			while( (line = reader.readLine()) != null ) {
				text = text + line + "\n";
			}
			reader.close();
		} catch( IOException ex ) {
			ex.printStackTrace();
			System.exit(1);
		}
		
		processHTML(text);
	}
	
	private void processHTML(String text) {
		//String[] parts = text.split("<tr><td align=\"right\" valign=\"top\">6<td valign=\"top\"><s>");
		String[] parts = text.split("<td valign=\"top\"><s>");

		List<List<String>> sentenceEvents = new ArrayList<List<String>>();
		// For each sentence.
		for( int xx = 1; xx < parts.length; xx++ ) {
			String[] subs = parts[xx].split("<sub>");
			// For each event in the sentence.
			List<String> ids = new ArrayList<String>();
			for( int ee = 1; ee < subs.length; ee++ ) {
				String eventID = subs[ee].substring(0,subs[ee].indexOf("<"));
				// Don't add preposition IDs (e.g., s13)
				if( eventID.startsWith("e") || eventID.startsWith("t") )
					ids.add(eventID);
		}
			sentenceEvents.add(ids);
		}
		
		// We now have all the event IDs in each sentence.
		// Create all pairs we need to annotate!
		allLinks = new LinkedHashMap<String, TLink>();
		allLinksHash = new HashSet<String>();
		String keypair;
		
		for( int sid = 0; sid < sentenceEvents.size(); sid++ ) {
			List<String> sent = sentenceEvents.get(sid);
			// Grab all pairs in this sentence.
			for( int xx = 0; xx < sent.size(); xx++ ) {
				// Create the link to the DCT (doc creation time).
				TLink dctlink = new EventEventLink(sent.get(xx), "t0", TLink.Type.NONE);
				keypair = dctlink.getId1() + " t0";
				allLinks.put(keypair, dctlink);
				allLinksHash.add(keypair);
				numRelations++;
				
				// Intra-sentence pairs.
				for( int yy = xx+1; yy < sent.size(); yy++ ) {
					TLink link = new EventEventLink(sent.get(xx), sent.get(yy), TLink.Type.NONE);
					keypair = link.getId1() + " " + link.getId2();
					allLinks.put(keypair, link);
					allLinksHash.add(keypair);
					numRelations++;
				}

				// Next sentence pairs.
				if( sid < sentenceEvents.size()-1 ) {
					List<String> nextSent = sentenceEvents.get(sid+1);
					for( int yy = 0; yy < nextSent.size(); yy++ ) {
						TLink link = new EventEventLink(sent.get(xx), nextSent.get(yy), TLink.Type.NONE);
						keypair = link.getId1() + " " + link.getId2();
						allLinks.put(keypair, link);
						allLinksHash.add(keypair);
						numRelations++;
					}
				}
				
			}
		}
	}
	
	/**
	 * Check that the string relation is a valid one that we expect.
	 */
	private boolean verify(String rel) {
	  // Stop now and save the file.
	  if( rel.equals("dump") || rel.equals("save") ) {
	    dumpToFile("progress.txt");
	    System.exit(1);
	  }
	  // Valid labels.
	  if( rel.equals("v") || rel.equals("i") || rel.equals("ii") || rel.equals("a") ||
	      rel.equals("b") || rel.equals("s") ) {
	    return true;
	  }
	  
	  // Not valid.
	  else if ( rel.equals("undo")) {
	  	// TODO: get rid of most recent labels. make sure nextkey variable is appropriately changed in prompt().
	  	return true;
	  }
	  else return false;
	}
	
	private void runClosure() {
//		System.out.println("Labeled relations before closure: " + labeledRelations);
    // Run Closure
	  List<TLink> added = new ArrayList<TLink>();
    List<TLink> newLinks = closure.computeClosure(labeledRelations);
    int count = 0;
    if( newLinks != null && newLinks.size() > 0 ) {
//      System.out.println("Added " + newLinks.size() + " transitive links.");
      for( TLink link : newLinks ) {
//        System.out.println("\tpossible closed link: " + link);
      	String keypair = link.getId1() + " " + link.getId2();
      	String keypairInverted = link.getId2() + " " + link.getId1();
        if( allLinksHash.contains(keypair) ) {
          labeledRelations.add(link);
          allLinks.get(keypair).setRelation(link.getRelation()); // change the TLink's relation to what was inferred
          pairsJustLabeled.add(keypair);
          count++;
          added.add(link); 
        }
        // Closure might produce B->A when we care about A->B. Invert and add it.
        else if( allLinksHash.contains(keypairInverted) ) {
//        	System.out.println("Closure produced inverted: " + link);
        	TLink ilink = createLink(link.getId2(), link.getId1(), TLink.invertRelation(link.getRelation()));
        	labeledRelations.add(ilink); //TODO: label link mapped to by keypairInverted appropriately
        	allLinks.get(keypairInverted).setRelation(ilink.getRelation()); // change the TLink's relation to what was inferred
        	pairsJustLabeled.add(keypairInverted);
          count++;
//          System.out.println("...adding inverted " + ilink);
          added.add(ilink);
        }
      }
    }
    // Output notification.
    if( count > 0 ) {
      System.out.println("Added " + count + " transitive links.");
      for( TLink link : added ) System.out.println("\t" + link);
    }
	}
	
	/**
	 * Convenience function that creates a TLink object from two event ids and a relation.
	 * It determines if the id's are events or times and creates the correct TLink subclass.
	 * @param id1 ID of the first event
	 * @param id2 ID of the second event
	 * @param rel The ordered relation for id1->id2
	 * @return A new TLink object.
	 */
	private TLink createLink(String id1, String id2, TLink.Type rel) {
    // See what type of relation we are adding (e.g. event-time)
    // YES, this depends on making sure all time variables start with 't'
		int times = 0;
    if( id1.charAt(0) == 't' ) times++;
    if( id2.charAt(0) == 't' ) times++;

    // Create the appropriate TLink
    TLink link = null;
    if( times == 2 ) link = new TimeTimeLink(id1, id2, rel, true);
    else if( times == 1 ) link = new EventTimeLink(id1, id2, rel, true);
    else link = new EventEventLink(id1, id2, rel, true);
    return link; 
	}
	
	/**
	 * Controls the main user interaction. We print one event pair, and accept the user's
	 * label for it. Save. Show the next pair.
	 */
	public void prompt() {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    int count = 0;
    
    // User help.
    System.out.println("Type 'save' to generate a text file of all annotations.");
    System.out.println("Valid relations: b a s i ii v");
    
    List<String> idKeys = new ArrayList<String>();
    for (String id : allLinks.keySet()) idKeys.add(id);
    curKey = 0;
    
		while( curKey < numRelations - 1 ) {  // Change from prior version: use while loop.
			TLink link = allLinks.get(idKeys.get(curKey));
		  String keypair = link.getId1() + " " + link.getId2();
		  
		  // If we haven't labeled this pair yet.
		  if( link.getRelation().equals(TLink.Type.NONE)) {
		    System.out.print(keypair + ": ");
		    // Keep getting feedback until user provides a valid response
		    try {
		      String userRelation = in.readLine();
		      while( !verify(userRelation) ) {
		        System.out.print("TRY AGAIN, " + keypair + ": ");
		        userRelation = in.readLine();
		      }
		      if ( userRelation.equals("undo")) {
		    	  TLink undoLink;
		    	  for ( String undoKeyPair: pairsJustLabeled ) {
		    		  undoLink = allLinks.get(undoKeyPair);
		    		  undoLink.setRelation(TLink.Type.NONE);
				      
		    	  }
		    	  curKey = curKey - 2;  // 
		     }
		      else {
		        //labeledLookup.put(keypair, userRelation);
		        pairsJustLabeled.clear();
		        link.setRelation(abbrevToRelation(userRelation));
		        labeledRelations.add(link);
		        pairsJustLabeled.add(keypair);
		        runClosure();  // Note that links added in runClosure are added to pairsJustLabeled
		     }
		      
		    } catch( IOException ex ) {
		      ex.printStackTrace();
		      System.exit(1);
		    }
	      count++;
		  }
		  if( count % 10 == 9 ) {
		    dumpToFile("auto-saved.txt");
		  }
		  curKey++;
		}
		dumpToFile("auto-saved.txt");
	}

	 private String relationToAbbrev(TLink.Type rel) {
	    if( rel == TLink.Type.BEFORE ) return "b";
	    else if( rel == TLink.Type.AFTER ) return "a";
	    else if( rel == TLink.Type.SIMULTANEOUS) return "s";
	    else if( rel == TLink.Type.INCLUDES ) return "i";
	    else if( rel == TLink.Type.IS_INCLUDED ) return "ii";
	    else return "";
	  }
	 
	private TLink.Type abbrevToRelation(String abbrev) {
	  if( abbrev.equalsIgnoreCase("b") ) return TLink.Type.BEFORE;
	  else if( abbrev.equalsIgnoreCase("a") ) return TLink.Type.AFTER;
	  else if( abbrev.equalsIgnoreCase("s") ) return TLink.Type.SIMULTANEOUS;
	  else if( abbrev.equalsIgnoreCase("i") ) return TLink.Type.INCLUDES;
	  else if( abbrev.equalsIgnoreCase("ii") ) return TLink.Type.IS_INCLUDED;
	  else if( abbrev.equalsIgnoreCase("v") ) return TLink.Type.VAGUE;
	  else return null;
	}
	
	/**
	 * Read a text file of relations...some have labels, some don't.
	 * Line format: <eid> <eid> [<label>]
	 * @param path Path to the file.
	 */
	public void loadProgressFile(String path) {
	  int count = 0;
	  try {
	    BufferedReader in = new BufferedReader(new FileReader(path));
	    String line;
	    while( (line = in.readLine()) != null ) {
	      String parts[] = line.split("\\s+");
	      if( parts.length > 3 || parts.length < 2) {
	        System.out.println("Unknown line format in file: " + line);
	        System.exit(1);
	      }
	      if( parts.length == 3 && parts[2].length() > 0 ) {
	        String keypair = parts[0] + " " + parts[1];
	        TLink link = new EventEventLink(parts[0], parts[1], abbrevToRelation(parts[2]));
	        allLinks.put(keypair, link);
	        labeledRelations.add(new EventEventLink(parts[0], parts[1], abbrevToRelation(parts[2])));
	        count++;
	      }
	    }
	    in.close();
    } catch( Exception ex ) { 
      ex.printStackTrace(); 
      System.exit(1);
    }

	}
	
	/**
	 * Write all the pairs to a file, and include the labels on those that were labeled!
	 * This output file can then be input again by this class and resumes labeling where you left off.
	 * @param path File to create.
	 */
	public void dumpToFile(String path) {
	  try {
	    PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(path)));
	    
	    for( String keypair : allLinks.keySet() ) {
	       TLink link = allLinks.get(keypair);
	       String label = relationToAbbrev(link.getRelation());
	       // Write to file.
	       writer.write(link.getId1() + "\t" + link.getId2());
	       writer.write("\t" + (label == null ? "" : label) + "\n");
	    }
	    writer.close();
	    System.out.println("Wrote current labels to file: " + path);
    } catch( Exception ex ) { 
      ex.printStackTrace(); 
      System.exit(1);
    }
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		Annotator2 anno = new Annotator2();
		if( args.length == 1 ) {
			if (args[0].contains(".apf.xml")){
				anno.readXML_ACE2005(args[0]);
				anno.prompt();
			}
			anno.readHTML(args[0]);
			anno.prompt();
		} 
		else if( args.length == 2 ) {
		  anno.readHTML(args[0]);
		  anno.loadProgressFile(args[1]);
      anno.prompt();
		}
		else {
			String className = Annotator2.class.getName();
			System.err.println("error: incorrect number of arguments");
			System.err.printf("usage: java %s html-file [progress-file]\n", className);
			System.exit(1);
		}
	}

}
