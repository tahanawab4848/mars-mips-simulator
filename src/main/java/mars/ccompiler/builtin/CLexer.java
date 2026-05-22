package mars.ccompiler.builtin;

import java.util.*;

/** Lexer: converts C source text into a list of CTokens. */
public class CLexer {

    private final String src;
    private int pos = 0, line = 1;

    private static final Map<String, CToken.Type> KEYWORDS = new HashMap<String, CToken.Type>();
    static {
        KEYWORDS.put("int",      CToken.Type.INT);
        KEYWORDS.put("char",     CToken.Type.CHAR);
        KEYWORDS.put("float",    CToken.Type.FLOAT);
        KEYWORDS.put("void",     CToken.Type.VOID);
        KEYWORDS.put("return",   CToken.Type.RETURN);
        KEYWORDS.put("if",       CToken.Type.IF);
        KEYWORDS.put("else",     CToken.Type.ELSE);
        KEYWORDS.put("while",    CToken.Type.WHILE);
        KEYWORDS.put("for",      CToken.Type.FOR);
        KEYWORDS.put("break",    CToken.Type.BREAK);
        KEYWORDS.put("continue", CToken.Type.CONTINUE);
    }

    public CLexer(String src) { this.src = src; }

    public List<CToken> tokenize() throws CTranspileException {
        List<CToken> tokens = new ArrayList<CToken>();
        while (pos < src.length()) {
            skipWhitespaceAndComments();
            if (pos >= src.length()) break;

            char c = src.charAt(pos);

            if (Character.isDigit(c)) { tokens.add(readNumber()); continue; }
            if (Character.isLetter(c) || c == '_') { tokens.add(readIdent()); continue; }
            if (c == '"') { tokens.add(readString()); continue; }
            if (c == '\'') { tokens.add(readChar()); continue; }

            // Operators / punctuation
            CToken tok = readOperator();
            if (tok != null) { tokens.add(tok); continue; }

            throw new CTranspileException("Unexpected character '" + c + "' at line " + line);
        }
        tokens.add(new CToken(CToken.Type.EOF, "", line));
        return tokens;
    }

    private void skipWhitespaceAndComments() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') { line++; pos++; }
            else if (Character.isWhitespace(c)) { pos++; }
            else if (c == '#') {
                // Skip preprocessor directives (e.g. #include <stdio.h>)
                while (pos < src.length() && src.charAt(pos) != '\n') pos++;
            }
            else if (pos + 1 < src.length() && c == '/' && src.charAt(pos+1) == '/') {
                while (pos < src.length() && src.charAt(pos) != '\n') pos++;
            } else if (pos + 1 < src.length() && c == '/' && src.charAt(pos+1) == '*') {
                pos += 2;
                while (pos + 1 < src.length() && !(src.charAt(pos) == '*' && src.charAt(pos+1) == '/')) {
                    if (src.charAt(pos) == '\n') line++;
                    pos++;
                }
                pos += 2;
            } else break;
        }
    }

    private CToken readNumber() {
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
        // skip suffix like f, L, u
        if (pos < src.length() && "fFuUlL".indexOf(src.charAt(pos)) >= 0) pos++;
        String val = src.substring(start, pos);
        return new CToken(val.contains(".") ? CToken.Type.FLOAT_LIT : CToken.Type.INT_LIT, val, line);
    }

    private CToken readIdent() {
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) pos++;
        String val = src.substring(start, pos);
        CToken.Type kw = KEYWORDS.get(val);
        return new CToken(kw != null ? kw : CToken.Type.IDENT, val, line);
    }

    private CToken readString() {
        pos++; // skip opening "
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && src.charAt(pos) != '"') {
            if (src.charAt(pos) == '\\') { pos++; sb.append(escape(src.charAt(pos))); }
            else sb.append(src.charAt(pos));
            pos++;
        }
        pos++; // skip closing "
        return new CToken(CToken.Type.STRING_LIT, sb.toString(), line);
    }

    private CToken readChar() {
        pos++; // skip '
        char c = src.charAt(pos++);
        if (c == '\\') c = escape(src.charAt(pos++));
        pos++; // skip '
        return new CToken(CToken.Type.CHAR_LIT, String.valueOf((int)c), line);
    }

    private char escape(char c) {
        switch (c) {
            case 'n': return '\n'; case 't': return '\t';
            case 'r': return '\r'; case '0': return '\0';
            default:  return c;
        }
    }

    private CToken readOperator() {
        char c = src.charAt(pos);
        char n = (pos+1 < src.length()) ? src.charAt(pos+1) : 0;
        CToken.Type t = null;
        int len = 2;
        if      (c=='=' && n=='=') t = CToken.Type.EQ;
        else if (c=='!' && n=='=') t = CToken.Type.NEQ;
        else if (c=='<' && n=='=') t = CToken.Type.LE;
        else if (c=='>' && n=='=') t = CToken.Type.GE;
        else if (c=='&' && n=='&') t = CToken.Type.AND;
        else if (c=='|' && n=='|') t = CToken.Type.OR;
        else if (c=='+' && n=='+') t = CToken.Type.INC;
        else if (c=='-' && n=='-') t = CToken.Type.DEC;
        else if (c=='+' && n=='=') t = CToken.Type.PLUS_ASSIGN;
        else if (c=='-' && n=='=') t = CToken.Type.MINUS_ASSIGN;
        else { len = 1;
            switch (c) {
                case '+': t=CToken.Type.PLUS; break;    case '-': t=CToken.Type.MINUS; break;
                case '*': t=CToken.Type.STAR; break;    case '/': t=CToken.Type.SLASH; break;
                case '%': t=CToken.Type.PERCENT; break; case '<': t=CToken.Type.LT; break;
                case '>': t=CToken.Type.GT; break;      case '=': t=CToken.Type.ASSIGN; break;
                case '!': t=CToken.Type.NOT; break;     case '&': t=CToken.Type.AMP; break;
                case '|': t=CToken.Type.PIPE; break;
                case '(': t=CToken.Type.LPAREN; break;  case ')': t=CToken.Type.RPAREN; break;
                case '{': t=CToken.Type.LBRACE; break;  case '}': t=CToken.Type.RBRACE; break;
                case '[': t=CToken.Type.LBRACKET; break;case ']': t=CToken.Type.RBRACKET; break;
                case ';': t=CToken.Type.SEMICOLON; break;case ',': t=CToken.Type.COMMA; break;
                default: return null;
            }
        }
        pos += len;
        return new CToken(t, src.substring(pos-len, pos), line);
    }
}
