# Identity và Equality

## <a id="identity-vs-equality">Object identity và logical equality</a>
Identity hỏi hai reference có cùng nhận diện một object hay không. Logical equality hỏi hai object có được xem là bằng nhau theo domain/value semantics hay không. Java dùng reference `==` cho identity và `equals` làm hook overridable cho logical equality.

## <a id="reference-equality">Reference equality bằng ==</a>
Với reference, `==` so identity (hoặc cả hai null), không so content. Nó phù hợp với enum constant, singleton identity, sentinel object hoặc khi thật sự hỏi identity. Nó thường sai cho value-like object như string, money value hay ID.

## <a id="value-object-equality">Mental model value-object equality</a>
Value object được nhận diện chủ yếu bởi meaningful value chứ không phải object identity. Equality thường nên stable, symmetric và dựa trên component immutable/effectively immutable. Equality design là domain contract, không chỉ boilerplate do IDE sinh.
