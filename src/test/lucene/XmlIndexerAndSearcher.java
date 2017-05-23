package test.lucene;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.NullFragmenter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.xml.sax.SAXException;

/**
 * Example application for Lucene. 
 * Index all xml files in a given directory and then opens a prompt where a user can supply search queries.
 * 
 *
 */
public class XmlIndexerAndSearcher {
	public static final int MAX_HIT_PER_PAGE = 10;
	public static final Version LUCENE_VERSION = Version.LUCENE_36;
	public static final String FILENAME_FIELD = "fileName";
	

	/**
	 * Definition of fields used for the citations example
	 */
	private static FieldDef[] FIELD_DEFS = new FieldDef[] {		
    	new FieldDef("phrase", Field.Store.YES, Field.Index.ANALYZED, "/quote/phrase"),
    	new FieldDef("author", Field.Store.YES, Field.Index.ANALYZED, "/quote/author")
    };
	
	/**
	 * Wrapper for field definitions in Lucene. Makes it possible to add xpaths and assign field definitions as constants
	 * 
	 */
	private static class FieldDef {
		String fieldName;		 
		Store fieldStore;
		Index fieldIndex;		
		XPathExpression valueXpathExpr;
		
		public FieldDef(String fieldName, Store fieldStore, Field.Index fieldIndex, String valueXpath) {
			this.fieldName = fieldName;			
			this.fieldStore = fieldStore;
			this.fieldIndex = fieldIndex;
			
			XPath xpath = XPathFactory.newInstance().newXPath();
			try {
				this.valueXpathExpr = xpath.compile(valueXpath);
			} catch (XPathExpressionException e) {
				throw new IllegalArgumentException("Invalid xpath: " + valueXpath, e);
			}
		}			
	}
	
    /**
     * Starts indexing all xml files and open a search prompt.
     * 
     * e.g of usage : java XmlIndexerAndSearcher C:/java/test/Lucene-test/src/xml/data C:/java/test/Lucene-test/src/xml/index
     * 
     * @param args	args[0]		Directory for data to index.
     * @param args	args[1]		Directory where index will be stored.   
     */
    public static void main(String[] args) throws IOException, SAXException, XPathExpressionException, ParserConfigurationException, ParseException, InvalidTokenOffsetsException {
    	if (args.length == 2) {
    		String xmlDataDirPath = args[0];
    		String indexDirPath = args[1];
    		
    		// verify directories exists and create list of files to index
    		final File xmlDataFileDir = new File(xmlDataDirPath);
    	    if (xmlDataFileDir.exists() && xmlDataFileDir.canRead()) {
    	    	final File indexFileDir = new File(indexDirPath);
    	    	if (indexFileDir.exists() && indexFileDir.canRead() && indexFileDir.canWrite()) {	    	    			    	    	
			    	File[] xmlFiles = xmlDataFileDir.listFiles(new FilenameFilter() {					
						@Override
						public boolean accept(File dir, String name) {
							return name.endsWith("xml");
						}
					});
			    	// start indexing all files
			    	XmlIndexerAndSearcher xmlIndexerAndSearcher = new XmlIndexerAndSearcher();
			    	xmlIndexerAndSearcher.indexFilesInDirectory(indexFileDir, xmlFiles);
			    	
			    	// start receive search queries
			    	xmlIndexerAndSearcher.startSearch(indexFileDir);
			    	
    	    	} else {
	    	    	System.err.println("Index directory '" +indexFileDir.getAbsolutePath()+ "' does not exist or is not writeable/readable, please check the path");	    	    	
	    	    }
    	    } else {
    	    	System.err.println("Document directory '" +xmlDataFileDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");	    	    	
    	    }	
    	} else {
    		System.err.println("Usage: test.lucene.XmlIndexerAndSearcher <data directory> <index directory>");
    	}	  
    	System.out.println("Finished");
    }
    
    /**
     * Index all supplied files and create new index or append to existing index.
     * 
     * @param indexFileDir	The directory where the index will be created/updated
     * @param xmlFiles		The xml files to index    
     */
    public void indexFilesInDirectory(File indexFileDir, File[] xmlFiles) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException {    	
    	Directory indexDir = FSDirectory.open(indexFileDir);
        Analyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
        IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, analyzer);
        config.setOpenMode(OpenMode.CREATE_OR_APPEND);        
        IndexWriter indexWriter = new IndexWriter(indexDir, config);
                			     
    	XmlIndexerAndSearcher xmlIndexer = new XmlIndexerAndSearcher();				    	
    	for (int i = 0; i < xmlFiles.length; i++) {
    		System.out.println("indexing " + xmlFiles[i]);
    		xmlIndexer.addFileToIndex(indexWriter, xmlFiles[i]);
    	}
    	indexWriter.forceMergeDeletes();
    	System.out.println("Total number of indexed documents: " + indexWriter.numDocs());    	
    	indexWriter.close();    	
    }
    
    /**
     * Adds a file to an open index.
     * 
     * @param indexWriter
     * @param xmlFile    
     */
    public void addFileToIndex(IndexWriter indexWriter, File xmlFile) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException  {
    	DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
    	domFactory.setNamespaceAware(true); 
    	DocumentBuilder builder = domFactory.newDocumentBuilder();
    	org.w3c.dom.Document xmlDoc = builder.parse(xmlFile);    	    	

    	Document luceneDoc = new Document();    	    	
    	luceneDoc.add(new Field(FILENAME_FIELD, xmlFile.getName(), Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
    	    	
    	for (int i = 0; i < FIELD_DEFS.length; i++) {
    		String value = (String) FIELD_DEFS[i].valueXpathExpr.evaluate(xmlDoc, XPathConstants.STRING);        		
    		luceneDoc.add(new Field(FIELD_DEFS[i].fieldName, value, FIELD_DEFS[i].fieldStore, FIELD_DEFS[i].fieldIndex));
    	}
    	Term fileNameTerm = new Term(FILENAME_FIELD, xmlFile.getName());    	
    	indexWriter.updateDocument(fileNameTerm, luceneDoc);    	
    }
    
    /**
     * Open search prompts to accept queries and then executes each query. Query prompt will be closed when an empty line is supplied.
     * 
     * @param indexFileDir	directory of index. 
     */
    public void startSearch(File indexFileDir) throws IOException, ParseException, InvalidTokenOffsetsException {
    	String queryStr = null;
    	Directory indexDir = FSDirectory.open(indexFileDir);
    	IndexReader indexReader = IndexReader.open(indexDir);
    	IndexSearcher searcher = new IndexSearcher(indexReader);
        Analyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
        
        while ( (queryStr = getQueryString()) != null) {
	        Query query = new QueryParser(Version.LUCENE_35, "phrase", analyzer).parse(queryStr);
	        TopScoreDocCollector collector = TopScoreDocCollector.create(MAX_HIT_PER_PAGE, true);
	        searcher.search(query, collector);	      
	        displayHits(collector, searcher, query);
        }
    }
    
    /**
     * Read query string
     * @return the entered query string
     */
    public String getQueryString() throws IOException {
    	BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    	System.out.println();
    	System.out.print("Enter query: ");
    	String queryString = in.readLine();
    	if (queryString.trim().length() == 0) {
    		queryString = null;
    	}
    	return queryString;
    }
    
    /**
     * Display search results including highlighting of query term in result.
     * 
     * @param collector the collector containing top rated hits
     * @param searcher
     * @param query
     */
    public void displayHits(TopScoreDocCollector collector, IndexSearcher searcher, Query query) throws CorruptIndexException, IOException, InvalidTokenOffsetsException {    	
    	ScoreDoc[] hits = collector.topDocs().scoreDocs;
    	QueryScorer queryScorer = new QueryScorer(query);
		Highlighter highlighter = new Highlighter(queryScorer);
		Fragmenter fragmenter = new NullFragmenter();		
		highlighter.setTextFragmenter(fragmenter);
		Analyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);		
		
		System.out.println("Found " + collector.getTotalHits() + " hits.");
    	System.out.println();
    	for(int i = 0; i < hits.length; ++i) {
    		Document doc = searcher.doc(hits[i].doc);
    		TokenStream stream = TokenSources.getAnyTokenStream(searcher.getIndexReader(), hits[i].doc, "phrase", doc, analyzer);    	    
    	    String bestFragment = highlighter.getBestFragment(stream, doc.get("phrase"));
    	    
    	    System.out.println("<Hit " + (i + 1) + ", schore " + hits[i].score + ">");
    	    System.out.println("Filename: " + doc.get(FILENAME_FIELD));
    	    System.out.println("Author: " + doc.get("author"));
    	    System.out.println("Phrase: " + doc.get("phrase"));
    	    System.out.println("Highlighted queryterms  in phrase: " + bestFragment);
    	    System.out.println();
    	}
    }      
}
