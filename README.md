A couple of years ago I worked at a company primarily concerned with information brokering. We developed a system to fetch addresses of companies given a set of selection criterias e.g geographic location, number of employees etc. The system used a search engine written in assembler and was deployed in an IBM mainframe.

The search engine was really fast and could match criterias against millions of documents within fraction of a second. The only problem was maintainance, there was only one guy who had the knowledge to make changes to the search engine application when we needed to adopt it for new requirements. At one time this guy was sick (hospitalized) and we discovered a serious bug so we called him on the phone and he instructed us to print the complete assembler program and (snail)mail it to him. After a couple of days we received a response mail containing some changes on a paper which we used to update the source code. After compiling and deploying the program the bug was resolved !

I started thinking that it would be interesting to see if one could develop a similar search engine in Java and started to browse the web for information about search engines and suitable algorithms. I quickly found out that the best algorithms and techniques had already been implemented in the open source project <a title="Apache Lucene" href="http://lucene.apache.org/core/" target="_blank">Apache Lucene</a>.
<h2>What is Lucene ?</h2>
Lucene (<a title="pronunciation" href="http://www.howjsay.com/index.php?word=lucene&amp;submit=Submit" target="_blank">pronunciation</a>) is a text search toolkit implemented in Java. It handles indexing and searching of text files, nothing more and nothing less.

The things I most appreciate about Lucene are:
<ul>
	<li>Excellent performance</li>
	<li>No fixed schema (ie no predefined data model)</li>
	<li>Very easy to use and to embed in other applications</li>
	<li>Modular design</li>
</ul>
The Lucene core is one Jar file approximately 1500 kb (latest release 3.6.1) without dependencies and its enough for basic usage (compare that to most other commercial bloatware). As stated before Lucene indexes and searches text files but it can also store binary contents in its index. If you want to index non textual content you can use the open source framework <a title="Tika" href="http://tika.apache.org/" target="_blank">Tika</a> which can extract text from almost any document format like ZIP, TAR, PDF, MP3, Microsoft OLE2 etc.

The Lucene core do not handle replication and scaling of the search index to several nodes but there are other sister projects like <a title="Solr" href="http://lucene.apache.org/solr/" target="_blank">Solr</a> that do. Solr is built on top of Lucene and adds all functionality needed for an enterprise search platform including replication, web crawling, administration gui, statistics etc.
<h2>Example application</h2>
I have written a Java class to demonstrate how easy it is to use Lucene. The program indexes XML files in a directory and lets you execute queries. To make it a little more interesting I have added support for updating (reindex) documents and highlighting of query terms.

The program comes with dependent jars and a set of  sample XML files containing famous quotations and can be downloaded <a href="https://github.com/milin44/lucene/blob/master/lucene-example.zip">here</a>.

You can start the application by executing java like this:

C:\java\test&gt;java -classpath lucene-example.jar;lucene-core-3.6.1.jar;lucene-highlighter-3.6.1.jar;lucene-memory-3.6.1.jar test.lucene.XmlIndexerAndSearcher "C:/java/test/data" "C:/java/test/index"

Example output after running the application:
<pre> indexing C:\java\test\data\quote1.xml
 indexing C:\java\test\data\quote10.xml
 indexing C:\java\test\data\quote2.xml
 indexing C:\java\test\data\quote3.xml
 indexing C:\java\test\data\quote4.xml
 indexing C:\java\test\data\quote5.xml
 indexing C:\java\test\data\quote6.xml
 indexing C:\java\test\data\quote7.xml
 indexing C:\java\test\data\quote8.xml
 indexing C:\java\test\data\quote9.xml
 Total number of indexed documents: 10

Enter query: man

Found 2 hits.
Filename: quote3.xml
Author: Unknown Author
Phrase: The richest man is not he who has the most, but he who needs the least

Highlighted queryterms in phrase: The richest <strong>man</strong> is not 
 he who has the most, but he who needs the least

Filename: quote10.xml
Author: Abraham Maslow
Phrase: To the man who only has a hammer, everything he encounters begins to look like a nail

Highlighted queryterms in phrase: To the <strong>man</strong> who only has a hammer, everything he encounters begins to look like a nail</pre>
Some comments on key parts in the application:
<pre>Analyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
IndexWriterConfig config =
   new IndexWriterConfig(LUCENE_VERSION, analyzer);
config.setOpenMode(OpenMode.CREATE_OR_APPEND);
IndexWriter indexWriter = new IndexWriter(indexDir, config);</pre>
Opens an existing index for appending data or creates a new one. The analyzer tokenizes the text before it is added to the index and makes it “searchable” e.g removing unnecessary words like “the”, “a” (aka “stop” words), deriving roots from verbs (reads, reading will be stored as “read”) etc.
<pre>indexWriter.forceMergeDeletes();
System.out.println("Total number of indexed documents: " +
indexWriter.numDocs());</pre>
When documents are deleted they can still exist in the index but are marked for deletion. Lucene will determine when its suitable do the actual deletion. Calling “forceMergeDeletes()” will force the deletion to happen immediately but should not be done in production system due to performance reasons. I have used it in the example so that the presentation of “total number of documents” will be correct even if you reindex the same documents more than once.
<pre>Document luceneDoc = new Document();
luceneDoc.add(new Field(FILENAME_FIELD, xmlFile.getName(),
   Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));</pre>
Defines a new field that are added to the index. Note that the definition of a field is dynamic (no fixed schema) and the same type of documents can, if needed, be indexed totally differently. To be able to identify each document the filename is added. Unique identifier fields are not needed by Lucene but makes it easier to update documents later.
<pre>Term fileNameTerm = new Term(FILENAME_FIELD, xmlFile.getName());
indexWriter.updateDocument(fileNameTerm, luceneDoc);</pre>
Deletes an existing document with the same filename and insert a new one.
<pre>Analyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
...
Query query = new QueryParser(LUCENE_VERSION, "phrase",
    analyzer).parse(queryStr);
TopScoreDocCollector collector =
    TopScoreDocCollector.create(MAX_HIT_PER_PAGE, true);
searcher.search(query, collector);</pre>
Create a query parser to search in a specified field. It also possible to search in several fields simultaneously. Note that the type of analyzer is the same as we used for storing the documents. This is because the query must be tokenized the same way as the data in the index to make them comparable.
<pre>Highlighter highlighter = new Highlighter(queryScorer);
Fragmenter fragmenter = new NullFragmenter();
highlighter.setTextFragmenter(fragmenter);</pre>
Used to highlight the query terms in the matching documents. In the example the whole text will be displayed together with markup elements ( tags). This is controlled by the fragmenter (in this case a “NullFragementer”). For larger texts other fragmenters should be used that only extract a few lines or sentences of text around the matched query terms.

&nbsp;
