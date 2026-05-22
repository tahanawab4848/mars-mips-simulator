package mars.ccompiler.builtin;

/** A lexical token produced by CLexer. */
public class CToken {
    public enum Type {
        // Literals & identifiers
        INT_LIT, FLOAT_LIT, STRING_LIT, CHAR_LIT, IDENT,
        // Keywords
        INT, CHAR, FLOAT, VOID, RETURN, IF, ELSE, WHILE, FOR, BREAK, CONTINUE,
        // Operators
        PLUS, MINUS, STAR, SLASH, PERCENT,
        EQ, NEQ, LT, LE, GT, GE,
        AND, OR, NOT,
        ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN,
        INC, DEC,
        AMP,  // & (address-of / bitwise and)
        PIPE, // |
        // Punctuation
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        SEMICOLON, COMMA,
        // Special
        EOF
    }

    public final Type   type;
    public final String value;
    public final int    line;

    public CToken(Type type, String value, int line) {
        this.type  = type;
        this.value = value;
        this.line  = line;
    }

    @Override public String toString() { return type + "(" + value + ")@" + line; }
}
