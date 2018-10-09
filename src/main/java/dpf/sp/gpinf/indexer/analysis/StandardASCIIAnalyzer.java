package dpf.sp.gpinf.indexer.analysis;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import java.io.IOException;
import java.io.Reader;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.util.Version;

/* [Triage] The following libraries are used to process tokens in a non-standard way */
import org.apache.lucene.analysis.miscellaneous.LengthFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.FilteringTokenFilter;

/*
 * Analisador de texto que utiliza o tokenizador LowerCaseLetterDigitTokenizer e
 * o filtro ASCIIFoldingFilter, o qual converte caracteres para seus equivalentes ascii,
 * removando acentos, cedilhas, etc.
 */
public class StandardASCIIAnalyzer extends Analyzer {

  private Set<?> stopSet;

  /**
   * Specifies whether deprecated acronyms should be replaced with HOST type. See
   * {@linkplain https://issues.apache.org/jira/browse/LUCENE-1068}
   */
  // private final boolean replaceInvalidAcronym,enableStopPositionIncrements;
  private final Version matchVersion;
  private final boolean pipeTokenizer;

  /**
   * Builds an analyzer with the default stop words.
   *
   * @param matchVersion Lucene version to match
   */
  public StandardASCIIAnalyzer(Version matchVersion, boolean pipeTokenizer) {
    this.matchVersion = matchVersion;
    this.pipeTokenizer = pipeTokenizer;
  }

  /**
   * Default maximum allowed token length
   */
  public static final int DEFAULT_MAX_TOKEN_LENGTH = 20; // [Triage] The maximum token length was reduced from 255 to 20

  private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

  /**
   * Set maximum allowed token length. If a token is seen that exceeds this length then it is
   * discarded. This setting only takes effect the next time tokenStream or reusableTokenStream is
   * called.
   */
  public void setMaxTokenLength(int length) {
    maxTokenLength = length;
  }

  /**
   * @see #setMaxTokenLength
   */
  public int getMaxTokenLength() {
    return maxTokenLength;
  }

  @Override
  protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {

    Tokenizer tokenizer;
    if (pipeTokenizer) {
      tokenizer = new CategoryTokenizer(matchVersion, reader);
    } else {
      tokenizer = new LetterDigitTokenizer(matchVersion, reader);
    }

    // src.setMaxTokenLength(maxTokenLength);
    TokenStream tok = new FastASCIIFoldingFilter(tokenizer);

    // tok = new StopFilter(matchVersion, tok, stopwords);
    
  /* [Triage] The following code removes tokens that exceed the maximum size or that contain non-latin characters (after being converted by the FastASCIIFoldingFilter).
     Nonetheless, the filters are not applied to the Category's description, which is checked by the following "if" */
    if (!(pipeTokenizer)) {
    	tok = new LengthFilter(matchVersion,tok, 1, maxTokenLength);
    	tok = new InvalidCharacterFilter(matchVersion,tok);
    }        
    
        
    return new TokenStreamComponents(tokenizer, tok) {
      @Override
      protected void setReader(final Reader reader) throws IOException {
        // src.setMaxTokenLength(StandardAnalyzer.this.maxTokenLength);
        super.setReader(reader);
      }
    };
  }
	
  
  /* [Triage] Filters that identifies tokens that contain non-latin Unicode characters */
  public class InvalidCharacterFilter extends FilteringTokenFilter { 
	  
	    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

	    public InvalidCharacterFilter(Version matchVersion, TokenStream tokenStream) {
	        super(matchVersion, tokenStream);
	    }

	    @Override
	    public boolean accept() {
	        	    	  	
	    	final char[] buffer = termAtt.buffer();
	        final int length = termAtt.length();

	        // Runs through each character of the token, checking for invalid ones
	        // If the token is clear, it returns true
	        for (int i = 0; i < length; i++) 
	        {
	          final char c = buffer[i];
	          if (c >= '\u0080') {	            
	            return false;
	          }
	        }
	        return true;
	    }
	}  
}
