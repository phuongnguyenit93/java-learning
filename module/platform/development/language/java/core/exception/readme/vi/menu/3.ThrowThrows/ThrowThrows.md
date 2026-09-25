# throw và throws

## <a id="throw-statement">throw statement</a>
`throw expression` chuyển control bằng cách ném một `Throwable` object. Code sau unconditional throw trên cùng path là unreachable. Nên throw exception meaningful và thêm context nhưng không leak sensitive data.

## <a id="throws-clause">throws declaration</a>
`throws` clause khai báo exception type mà method có thể propagate theo checked-exception rule. Nó tự thân không throw gì và không guarantee method sẽ fail. Unchecked exception có thể document trong `throws` nhưng compiler không yêu cầu.

## <a id="precise-rethrow">Precise rethrow typing</a>
Compiler có thể infer tập checked exception hẹp hơn khi catch parameter effectively final và thrown value đến từ các alternative đã biết. Vì vậy rethrow catch variable có thể giữ `throws` contract precise hơn syntactic catch type.

## <a id="override-throws-rules">Override không được broaden checked exception</a>
Overriding method không được thêm checked exception rộng hơn/mới mà parent contract không cho phép. Nếu cho phép, code compile theo parent type có thể gặp checked failure mà nó chưa bao giờ bị yêu cầu handle.

## <a id="checked-exception-narrowing">Override có thể giữ, narrow hoặc bỏ checked exception</a>
Child override có thể declare cùng checked exception, subtype hoặc không declare checked exception. Unchecked exception không bị constrain giống vậy. Đây là một phần substitutability của parent contract.
