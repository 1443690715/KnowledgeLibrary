package com.lib.utils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.CoreSynonymDictionary;
import com.hankcs.lucene.HanLPAnalyzer;
import com.hankcs.lucene.HanLPTokenizer;
import com.lib.entity.FileInfo;
import com.lib.enums.Const;
/**
 * 搜索索引 Lucene 5.5+
 * 
 * @author Administrator
 * 
 */
public class LuceneSearchUtil {
	/**
	 * 根据索引搜索doc
	 * @param text
	 * @param pageNo
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */
	//上一次检索条件
	private static Object oldBooleanQuery=null;
	//索引存放路径
	private static String indexPath=Const.ROOT_PATH+"lucene";
	//保存索引结果，分页中使用
	private static TopDocs hits=null;
	//分词器
	private static Analyzer analyzer =  new HanLPAnalyzer() {
		@Override
		protected TokenStreamComponents createComponents(String arg0) {
			Tokenizer tokenizer = new HanLPTokenizer(HanLP.newSegment().enableIndexMode(true).enableJapaneseNameRecognize(true)
		               .enableIndexMode(true).enableNameRecognize(true).enablePlaceRecognize(true), null, false);
			return new TokenStreamComponents(tokenizer);
		}
	};
	//字段查询条件
	private static Query queryText = null;
	/**
	 * 
	 * @param file 
	 * @param pageNo 页数
	 * @param fileClassId 类型id
	 * @param flag //是否二次查询条件
	 * @return
	 */
	public static void indexDocSearch(FileInfo file, Integer pageNo, List<Long> fileClassId,int flag){
		
		//不是第一页直接返回分页结果
		if(pageNo!=1)
		{
			page(pageNo,12);
		}
		// 保存索引文件的地方
		Directory directory=null;
		// IndexReader reader=DirectoryReader
		DirectoryReader ireader=null;
		// 创建 IndexSearcher对象，相比IndexWriter对象，这个参数就要提供一个索引的目录就行了
		IndexSearcher indexSearch = null;
		
		try {
			directory = FSDirectory.open(new File(indexPath).toPath());
			ireader = DirectoryReader.open(directory);
			indexSearch = new IndexSearcher(ireader);
		    // 多个条件组合查询
		    BooleanQuery booleanQuery = new BooleanQuery();
		
		//判断是否二次查询
		if(flag==1)
			booleanQuery.add((BooleanQuery)oldBooleanQuery,BooleanClause.Occur.MUST);
			else{
				oldBooleanQuery=null;
		}
		
		// 查询条件一 字段查询
	    queryText = null;
		if (file.getFileName() != null && !"".equals(file.getFileName())) {
			
			String[] fields = { "fileName", "fileText", "fileBrief","fileKeyWord"};
			Map<String, Float> boost = new HashMap<String, Float>();
			boost.put("fileKeyWord", 4.0f);
			boost.put("fileName", 3.0f);
			boost.put("fileBrief", 2.0f);
			boost.put("fileText", 1.0f);
			// 创建QueryParser对象,第一个表示搜索Field的字段,第二个表示搜索使用分词器
			QueryParser queryParser = new MultiFieldQueryParser(fields, analyzer, boost);
			// 生成Query对象
			queryText = queryParser.parse(file.getFileName());
			booleanQuery.add(queryText, BooleanClause.Occur.MUST);
		}
		
		// 查询条件二日期
		Date sDate = file.getFileCreateTime();
		Date eDate = file.getFileCreateTime();//TODO
		if ((sDate == null || "".equals(sDate)) && (eDate != null && !"".equals(eDate))) {
			Calendar calendar = Calendar.getInstance();
			calendar.set(1900, 0, 1);
			sDate = calendar.getTime();
		}
		
		//若只有起始值结束值默认为当天
		if ((sDate != null && !"".equals(sDate)) && (eDate == null || "".equals(eDate))) {
			eDate = new Date();
		}
		if ((sDate != null && !"".equals(sDate)) && (eDate != null || !"".equals(eDate))) {

			// Lucene日期转换格式不准，改用format格式
			// sDateStr=DateTools.dateToString(sDate,
			// DateTools.Resolution.MINUTE);
			// eDateStr=DateTools.dateToString(eDate,
			// DateTools.Resolution.MINUTE);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
			BytesRef sDateStr = new BytesRef(sdf.format(sDate));
			BytesRef eDateStr = new BytesRef(sdf.format(eDate));

			// 时间范围查询
			Query queryUpTime = new TermRangeQuery("fileCreateTime", sDateStr, eDateStr, true, true);
			Query queryDocTime = new TermRangeQuery("fileCreateTime", sDateStr, eDateStr, true, true);//TODO
			booleanQuery.add(queryUpTime, BooleanClause.Occur.MUST);
			booleanQuery.add(queryDocTime, BooleanClause.Occur.MUST);
		}
		// 查询条件三分类查询
		for (Long id : fileClassId) {
			TermQuery termQuery = new TermQuery(new Term("fileClassId", id+""));
			booleanQuery.add(termQuery, BooleanClause.Occur.SHOULD);
		}
		// 查询条件四类型查询
		if (file.getFileExt() != null && !"".equals(file.getFileExt())) {
			List<String> typeList = null;
			if (file.getFileExt().equals("office")) {
				typeList = JudgeUtils.officeFile;
			}
			if (file.getFileExt().equals("video")) {
				typeList = JudgeUtils.videoFile;
			}
			if (file.getFileExt().equals("img")) {
				typeList = JudgeUtils.imageFile;
			}
			for (String type : typeList) {
				TermQuery termQuery = new TermQuery(new Term("fileExt", type));
				booleanQuery.add(termQuery, BooleanClause.Occur.SHOULD);
			}
		}
		oldBooleanQuery=booleanQuery;
		// 搜索结果 TopDocs里面有scoreDocs[]数组，里面保存着索引值
		hits = indexSearch.search(booleanQuery, 100000);
		page(pageNo,12);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (ireader != null) {
					ireader.close();
				}
				if (directory != null) {
					directory.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	
	}
	/**
	 * 包装每页的file
	 * @param pageNo 页数
	 * @param num 每页数量
	 * @return
	 */
	private static List<Map<String, String>> page(Integer pageNo,Integer num){
		
		// 保存索引文件的地方
		Directory directory=null;
		// IndexReader reader=DirectoryReader
		DirectoryReader ireader=null;
		// 创建 IndexSearcher对象，相比IndexWriter对象，这个参数就要提供一个索引的目录就行了
		IndexSearcher indexSearch = null;
		// 循环hits.scoreDocs数据，并使用indexSearch.doc方法把Document还原，再拿出对应的字段的值
		ScoreDoc[] scoreDoc = hits.scoreDocs;
		//每页的file
		List<Map<String, String>> page = new ArrayList<Map<String, String>>();
		try {
			directory = FSDirectory.open(new File(indexPath).toPath());
			ireader = DirectoryReader.open(directory);
			indexSearch = new IndexSearcher(ireader);
			for (int i = pageNo * num, j = 0; i < scoreDoc.length && j < num; i++, j++) {
				Map<String, String> map = new HashMap<String, String>();
				int fileId = scoreDoc[i].doc;
				Document file = indexSearch.doc(fileId);
				map.put("fileId", file.get("fileId"));
				
				String fileName = "";
				if (file.get("fileName") != null && !"".equals(file.get("fileName")))
					fileName = displayHtmlHighlight(queryText, analyzer, "fileName", file.get("fileName"), 30);
				map.put("fileName", fileName);
				
				String fileBrief = "";
				if (file.get("fileBrief")!= null && !"".equals(file.get("fileBrief")))
					fileBrief = displayHtmlHighlight(queryText, analyzer, "fileBrief", file.get("fileBrief"), 30);
				map.put("fileBrief", fileBrief);
				
				String fileText = "";
				if (file.get("fileText") != null&& !"".equals(file.get("fileText"))) {
					fileText = displayHtmlHighlight(queryText, analyzer, "fileText", file.get("fileText"), 30);
				}
				map.put("fileText", fileText);
				
				String fileKeyWord = "";
				if (file.get("fileKeyWord") != null&& !"".equals(file.get("fileKeyWord"))) {
					fileKeyWord = displayHtmlHighlight(queryText, analyzer, "fileKeyWord", file.get("fileKeyWord"), 30);
				}
				map.put("fileKeyWord", fileKeyWord);
				
				page.add(map);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (ireader != null) {
					ireader.close();
				}
				if (directory != null) {
					directory.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return page;
	}
	
	/**
	 * 获取简介
	 * @param docId
	 * @param docName
	 * @return
	 * @throws IOException 
	 * @throws InvalidTokenOffsetsException 
	 * @throws ParseException 
	 */
	public static String extractSummary(Long fileId, String fileName) {
		
		// 保存索引文件的地方
		Directory directory=null;
		// IndexReader reader=DirectoryReader
		DirectoryReader ireader=null;
		// 创建 IndexSearcher对象，相比IndexWriter对象，这个参数就要提供一个索引的目录就行了
		IndexSearcher indexSearch = null;
		// 生成Query对象
		Query query = null;
		//new一个文档对象
		Document document = new Document();
		//文件简介
		String filebriefs="";
		try {
			directory = FSDirectory.open(new File(indexPath).toPath());

			ireader = DirectoryReader.open(directory);

			indexSearch = new IndexSearcher(ireader);

			// Term对象
			Term term = new Term("fileId", fileId.toString());

			TermQuery termQuery = new TermQuery(term);

			TopDocs topdocs = indexSearch.search(termQuery, 1);
			document = indexSearch.doc(topdocs.scoreDocs[0].doc);

			if (document.get("fileText") != null && !"".equals(document.get("fileText"))) {

				QueryParser queryParser = new QueryParser("docText", analyzer);

				String fileText = "";
				if (fileName != null && !"".equals(fileName)) {
					query = queryParser.parse(fileName);
					fileText = displayHtmlHighlight(query, analyzer, "fileText", document.get("fileText"), 2000);
					if (fileText != "")
						filebriefs = HanLP.getSummary(fileText, 3);
				} else {
					fileText = document.get("fileText");
					for (String filebrief : HanLP.extractSummary(fileText, 3)) {
						filebriefs = filebriefs + filebrief + "。";
					}
					// strs.add(HanLP.getSummary(str, 100).toString());
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (ireader != null) {
					ireader.close();
				}
				if (directory != null) {
					directory.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return filebriefs;
		
	}
	/**
	/**
	 * 获取主题词
	 * @param docId
	 * @return
	 * @throws IOException 
	 */
	public static List<String> extractKeyword(Long fileId)  {
		
		// 保存索引文件的地方
		Directory directory=null;
		// IndexReader reader=DirectoryReader
		DirectoryReader ireader=null;
		// 创建 IndexSearcher对象，相比IndexWriter对象，这个参数就要提供一个索引的目录就行了
		IndexSearcher indexSearch = null;
		//new一个文档对象
		Document document = new Document();
		//主题词变量
		List<String> fileKeyWords=new ArrayList<String>();
		try {
		directory = FSDirectory.open(new File(indexPath).toPath());
		
		ireader = DirectoryReader.open(directory);
		
		indexSearch = new IndexSearcher(ireader);
		
		Term term = new Term("fileId", fileId.toString());
		TermQuery termQuery = new TermQuery(term);
		// System.out.println(termQuery);
		TopDocs topdocs = indexSearch.search(termQuery, 1);
		
		document = indexSearch.doc(topdocs.scoreDocs[0].doc);
	
		if (document.get("fileText") != null && !"".equals(document.get("fileText"))) 
			fileKeyWords = HanLP.extractKeyword(document.get("fileText"), 3);
		
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (ireader != null) {
					ireader.close();
				}
				if (directory != null) {
					directory.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return fileKeyWords;
	}
	/**
	 * 查找关联文档ids
	 * @param strs 关联词
	 * @return
	 * @throws IOException 
	 */
	public static List<Long> extractText(List<String> fileKeyWords){
		//new一个文档对象
		Document document = new Document();
		//关联文档的id
		List<Long> fileIds = new ArrayList<Long>();
		// 保存索引文件的地方
		Directory directory = null;
		// IndexReader reader=DirectoryReader
		DirectoryReader ireader = null;
		// 创建 IndexSearcher对象，相比IndexWriter对象，这个参数就要提供一个索引的目录就行了
		IndexSearcher indexSearch = null;
		 //多个条件组合查询
		BooleanQuery booleanQuery = new BooleanQuery();
		
		try {
			directory = FSDirectory.open(new File(indexPath).toPath());
			
			ireader = DirectoryReader.open(directory);
			
			indexSearch = new IndexSearcher(ireader);
		
		for (String fileKeyWord : fileKeyWords) {
			TermQuery termQuery = new TermQuery(new Term("docKeyWord", fileKeyWord));
			booleanQuery.add(termQuery, BooleanClause.Occur.SHOULD);
		}
		// System.out.println(termQuery);
		TopDocs topdocs = indexSearch.search(booleanQuery, 5);
		// System.out.println("共找到" + topdocs.scoreDocs.length + ":条记录");
		for (ScoreDoc scoreDocs : topdocs.scoreDocs) {
			int documentId = scoreDocs.doc;
			document = indexSearch.doc(documentId);
			fileIds.add(Long.valueOf(document.get("fileId")));
		}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (ireader != null) {
					ireader.close();
				}
				if (directory != null) {
					directory.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return fileIds;
	}
	
	static String displayHtmlHighlight(Query query, Analyzer analyzer, String fieldName, String fieldContent,
			int fragmentSize) throws IOException, InvalidTokenOffsetsException {
		// 创建一个高亮器
		Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("<font color='#c00'>", "</font>"),
				new QueryScorer(query));
		Fragmenter fragmenter = new SimpleFragmenter(fragmentSize);
		highlighter.setTextFragmenter(fragmenter);
		return highlighter.getBestFragment(analyzer, fieldName, fieldContent);
	}
	
}