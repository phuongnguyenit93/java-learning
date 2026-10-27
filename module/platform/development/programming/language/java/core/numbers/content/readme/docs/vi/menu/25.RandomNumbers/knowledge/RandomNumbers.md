<a id="back-to-top"></a>

# Số ngẫu nhiên

## Menu
- [Số giả ngẫu nhiên, seed và yêu cầu tái lập](#random-numbers-001)
- [Random và chuỗi giá trị theo seed](#random-numbers-002)
- [Math.random và giới hạn sử dụng](#random-numbers-003)
- [ThreadLocalRandom khi nhiều luồng sinh số](#random-numbers-004)
- [SplittableRandom và chia nguồn ngẫu nhiên](#random-numbers-005)
- [RandomGenerator và RandomGeneratorFactory từ Java 17](#random-numbers-006)
- [Các khoảng origin, bound và quy tắc cận trên loại trừ](#random-numbers-007)
- [Sinh số nguyên, số thực và phân phối Gaussian](#random-numbers-008)
- [Vì sao dùng phép % tùy tiện có thể tạo thiên lệch](#random-numbers-009)
- [SecureRandom cho yêu cầu không thể dự đoán](#random-numbers-010)
- [Chọn thuật toán, seed và cách kiểm thử mà không nhầm với bảo đảm bảo mật](#random-numbers-011)
- [Stream giá trị ngẫu nhiên và giới hạn kích thước](#random-numbers-012)

## <a id="random-numbers-001">Số giả ngẫu nhiên, seed và yêu cầu tái lập</a>

<details>
<summary>Xem chi tiết</summary>

# Số giả ngẫu nhiên, seed và yêu cầu tái lập trong Java

## 1. PRNG có trạng thái: seed là điểm khởi đầu

**Pseudorandom number generator (PRNG)** tạo dãy trông ngẫu nhiên bằng một thuật toán xác định và trạng thái nội bộ. Seed (hạt giống) giúp khởi tạo trạng thái; sau mỗi lần sinh giá trị, trạng thái thường tiến tới vị trí mới:

```text
seed --> S0 --next--> r1,S1 --next--> r2,S2 --next--> r3,S3 ...
```

Ba mục tiêu **khác nhau** khi dùng nguồn ngẫu nhiên:

- **Exact replay:** phát lại đúng từng giá trị của một lần chạy.
- **Chất lượng thống kê:** dãy có phân phối/tương quan phù hợp mô phỏng.
- **Khó dự đoán:** kẻ quan sát không thể đoán được token, mã xác minh hoặc dữ liệu bí mật tiếp theo bằng cách tính toán khả thi.

Một bộ sinh cố định seed giúp test tái lập nhưng **không tự an toàn mật mã**. `java.util.Random` phục vụ mô phỏng thông thường; dữ liệu cần chống dự đoán sử dụng `java.security.SecureRandom` cùng quản lý token an toàn.

## 2. Vì sao cùng seed chưa chắc cùng kết quả nghiệp vụ?

```java
import java.util.Random;

Random first = new Random(42L);
Random second = new Random(42L);
int a = first.nextInt(100);   // 30
int b = first.nextInt(100);   // 63
int c = second.nextInt(100);  // 30
```

Hai đối tượng riêng có cùng seed cho cùng chuỗi **nếu cùng thuật toán, cùng lời gọi và thứ tự**. Đối tượng `first` đã ở vị trí thứ hai khi lấy `b`, trong khi `second` mới lấy số đầu tiên. Nếu thêm lần rút cho thời tiết trước phần thưởng trong trò chơi, số dành cho phần thưởng có thể thay đổi, dù seed vẫn bằng 42.

| Yếu tố khác nhau | Hệ quả |
| --- | --- |
| Thuật toán Random và SplittableRandom | Seed giống nhau không tạo thuật toán chung |
| Số lần/thứ tự gọi nextInt, nextLong, nextGaussian | Trạng thái có thể tiến số bước khác nhau |
| Bound/origin hay một nhánh điều kiện mới | Kết quả hoặc lịch sử tiêu thụ thay đổi |
| Các luồng dùng chung một generator | Thứ tự task nhận số phụ thuộc lịch scheduler |
| Input hoặc phiên bản mã thay đổi | Số lượt rút có thể không tương ứng |

Với `java.util.Random` cổ điển, thuật toán sử dụng trạng thái 48 bit và dãy kết quả với cùng seed/lịch gọi được đặc tả. Bài kế tiếp giải thích `Random.setSeed` và chi tiết 48 bit; các bài riêng bàn về generator theo thread, splitting và factory của Java 17.

## 3. Tái lập giống nhau hay tái lập về thống kê?

**Exact replay** yêu cầu các giá trị tại mỗi bước trùng nhau. Nó cần seed, tên thuật toán, lịch sử gọi và hoàn cảnh chạy được kiểm soát. Nếu một công việc đã đi nửa chặng, seed ban đầu **không** cho biết bộ sinh đang ở trạng thái nào; cần chạy lại cùng lịch sử hoặc lưu checkpoint khi có cách phù hợp.

**Statistical reproducibility** chỉ yêu cầu tính chất thống kê hoặc phân phối tương tự giữa các lần thử; không bắt buộc từng giá trị khớp nhau. Một seed cố định hữu ích cho regression test nhưng không chứng minh tính đúng của mô phỏng ở mọi seed, tính đều hay khả năng chống dự đoán.

Với mô phỏng song song, đừng dùng chung một đối tượng rồi mặc định mỗi task nhận cùng dãy giữa hai lần chạy: các thread có thể rút số theo thứ tự khác. Có thể dùng generator riêng theo **định danh task ổn định**, hoặc tách generator bằng `split()` theo lịch chia cố định. Những seed khác nhau không tự chứng minh các dãy độc lập thống kê; phải xét thuật toán và phương thức quản lý stream.

## 4. Ghi gì để tái hiện ca lỗi?

Với dữ liệu **không chứa bí mật**, hãy lưu tối thiểu:

1. **Tên lớp/thuật toán**, chẳng hạn `java.util.Random` hoặc `L64X128MixRandom` (Java 17).
2. **Seed thực tế** cùng cơ chế khởi tạo; nếu không ghi seed tự động, khó phát lại.
3. **Thứ tự lời gọi** như năm lần `nextInt(100)`, các bound/origin và số lần chia generator.
4. **Phiên bản mã và dữ liệu đầu vào** nếu chúng ảnh hưởng nhánh và lịch tiêu thụ.
5. **Cách phân công task→generator** cùng quy tắc chia seed khi workload chạy song song.

Không ghi seed/trạng thái CSPRNG hay token bảo mật nhằm mục đích replay như seed mô phỏng; loại thông tin này có thể chính là bí mật.

## 5. Ví dụ Java 17 chạy được

Lưu thành `RandomSeedReplayDemo.java`:

```java
import java.util.Arrays;
import java.util.Random;

public class RandomSeedReplayDemo {
    private static int[] samples(long seed, int count) {
        Random rng = new Random(seed);
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = rng.nextInt(100);
        }
        return values;
    }

    public static void main(String[] args) {
        int[] first = samples(42L, 5);
        int[] replay = samples(42L, 5);
        System.out.println(Arrays.toString(first));
        System.out.println("replay=" + Arrays.equals(first, replay));

        Random shifted = new Random(42L);
        shifted.nextInt(100);
        System.out.println("shiftedFirst=" + shifted.nextInt(100));
    }
}
```

```bash
javac --release 17 RandomSeedReplayDemo.java
java RandomSeedReplayDemo
```

```text
[30, 63, 48, 84, 70]
replay=true
shiftedFirst=63
```

Đây là kiểm tra khả năng tái lập dãy, không phải phép đo chất lượng thống kê hay kiểm chứng bảo mật.

## 6. Chọn API theo mục đích

| Nhu cầu | Hướng tiếp cận |
| --- | --- |
| Test/mô phỏng tuần tự cần phát lại | `new Random(seed)` và lịch gọi được kiểm soát |
| Đa luồng không cần seed tường minh | `ThreadLocalRandom.current()` trong worker |
| Cấp bộ sinh cho từng nhánh task | `SplittableRandom.split()` theo lịch chia rõ ràng |
| Cấu hình thuật toán Java 17 | `RandomGeneratorFactory.of(name).create(seed)` nếu thuật toán hỗ trợ seed |
| Sinh token/bí mật | `SecureRandom` và chính sách bảo vệ vòng đời dữ liệu |

**Ghi nhớ:** seed khởi tạo PRNG nhưng kết quả cuối phụ thuộc cả thuật toán, trình tự gọi và điều kiện xử lý. Tái lập và chống dự đoán là hai mục tiêu riêng, cần chọn đúng API.

**Tài liệu:** [Random](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html), [RandomGenerator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/RandomGenerator.html), [SplittableRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SplittableRandom.html), [SecureRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-002">Random và chuỗi giá trị theo seed</a>

<details>
<summary>Xem chi tiết</summary>

# Random và chuỗi giá trị theo seed trong Java

## 1. Seed là gì và tại sao cùng seed có thể phát lại cùng chuỗi?

`java.util.Random` là bộ sinh **số giả ngẫu nhiên** (pseudorandom number generator, PRNG). Thay vì tạo ra sự ngẫu nhiên tuyệt đối, đối tượng giữ một **trạng thái nội bộ**; mỗi lần chương trình yêu cầu số tiếp theo, thuật toán cập nhật trạng thái và tính ra kết quả. Giá trị khởi tạo trạng thái được gọi là **seed** (hạt giống).

~~~java
Random random = new Random(42L);
int value = random.nextInt(100);
~~~

Trong ví dụ, `42L` là seed; `nextInt(100)` sinh một số nguyên từ **0 (bao gồm) đến 100 (không bao gồm)**, tức từ 0 đến 99.

Thuộc tính quan trọng:

> Với **cùng triển khai thuật toán, cùng seed, cùng các lời gọi theo cùng thứ tự và cùng tham số**, ta nhận được cùng một chuỗi kết quả.

Đây là nền tảng để phát lại mô phỏng, tái hiện lỗi, tạo dữ liệu kiểm thử và so sánh hai lần chạy thí nghiệm. Một seed cố định giúp **tái lập** (reproducibility); nó không làm giá trị sinh ra trở thành bí mật hoặc an toàn về mật mã.

Cần phân biệt:

- **Seed** là dữ liệu dùng để thiết lập trạng thái ban đầu.
- **Trạng thái hiện tại** thay đổi khi tiêu thụ số ngẫu nhiên. Sau khi gọi phương thức sinh số, đối tượng thường không còn ở trạng thái ban đầu nữa.
- **Chuỗi giá trị** được xác định bởi seed **và** trình tự sử dụng bộ sinh, không chỉ bởi seed đứng riêng.

## 2. Hai Random cùng seed: giống chuỗi, không chia sẻ trạng thái

Xét hai đối tượng độc lập:

~~~java
Random first = new Random(42L);
Random second = new Random(42L);
~~~

Nếu cả hai đều lần lượt gọi `nextInt(100)` năm lần, kết quả của từng vị trí trùng nhau:

| Lần gọi | `first.nextInt(100)` | `second.nextInt(100)` |
| ---: | ---: | ---: |
| 1 | 30 | 30 |
| 2 | 63 | 63 |
| 3 | 48 | 48 |
| 4 | 84 | 84 |
| 5 | 70 | 70 |

Hai đối tượng **không phải cùng một đối tượng**: tăng trạng thái của `first` không tự làm `second` tăng theo. Chúng chỉ tạo cùng chuỗi khi được khởi tạo từ cùng seed và tiêu thụ lời gọi tương ứng.

Ngược lại, nếu một đối tượng đã gọi thêm một lần, vị trí đọc kế tiếp của hai đối tượng có thể khác:

~~~text
Random A (seed 42): nextInt(100) -> 30, rồi -> 63
Random B (seed 42): chưa gọi   -> 30 ở lời gọi đầu tiên
~~~

Vì thế, một câu hỏi chính xác hơn “seed giống nhau chưa?” là: **hai bộ sinh có đang ở cùng trạng thái trước lời gọi và sử dụng cùng phương thức/tham số không?**

## 3. Ví dụ Java 17 hoàn chỉnh với kết quả xác định

Tạo file `SeededRandomDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SeededRandomDemo {
    private static List<Integer> generate(long seed, int count) {
        Random random = new Random(seed);
        List<Integer> values = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            values.add(random.nextInt(100));
        }
        return values;
    }

    public static void main(String[] args) {
        List<Integer> first = generate(42L, 5);
        List<Integer> replay = generate(42L, 5);

        System.out.println("Seed 42: " + first);
        System.out.println("Replay identical: " + first.equals(replay));

        Random random = new Random(42L);
        System.out.println("Draw 1: " + random.nextInt(100));
        System.out.println("Draw 2: " + random.nextInt(100));

        random.setSeed(42L);
        System.out.println("After setSeed, draw 1: "
                + random.nextInt(100));

        Random restartedEachTime = new Random(42L);
        int fixedFirst = restartedEachTime.nextInt(100);
        int sameFirst = new Random(42L).nextInt(100);
        System.out.println("Two fresh generators: "
                + fixedFirst + ", " + sameFirst);

        Random anotherSeed = new Random(123L);
        Random replayAnotherSeed = new Random(123L);
        System.out.println("Seed 123 reproducible: "
                + (anotherSeed.nextLong()
                    == replayAnotherSeed.nextLong()));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac SeededRandomDemo.java
java SeededRandomDemo
~~~

Kết quả:

~~~text
Seed 42: [30, 63, 48, 84, 70]
Replay identical: true
Draw 1: 30
Draw 2: 63
After setSeed, draw 1: 30
Two fresh generators: 30, 30
Seed 123 reproducible: true
~~~

Giải thích:

1. Hai lần `generate(42L, 5)` tạo hai đối tượng `Random` mới nhưng áp dụng **cùng seed và năm lời gọi `nextInt(100)` theo cùng thứ tự**, nên hai danh sách bằng nhau.
2. Một `Random` được sử dụng liên tục không trả mãi giá trị đầu tiên: lần gọi kế tiếp dùng trạng thái tiếp theo.
3. `setSeed(42L)` **thiết lập lại chuỗi** của đối tượng đó. Sau khi đặt lại cùng seed, lời gọi đầu tiên lại trả `30`.
4. Tạo `new Random(42L)` mỗi lần lặp sẽ **khởi động lại từ đầu**, nên dễ lấy lặp đi lặp lại giá trị `30`; đây là lỗi phổ biến khi muốn sinh nhiều số khác nhau.
5. Với seed 123, hai lời gọi `nextLong()` đầu tiên trên hai đối tượng mới cho cùng kết quả. Ví dụ chỉ kiểm tra tính tái lập, không cần ghi nhớ giá trị `long` thực tế.

Các giá trị trong ví dụ thuộc `java.util.Random`, không phải cam kết chung cho tất cả bộ sinh ngẫu nhiên khác trong Java.

## 4. Bên trong java.util.Random: seed quyết định chuỗi như thế nào?

Với lớp `java.util.Random` cổ điển, thuật toán cốt lõi là một **linear congruential generator (LCG)** sử dụng trạng thái **48 bit**. Ở mức khái niệm, nó thực hiện:

~~~text
Trạng thái ban đầu 48 bit = (seed XOR 0x5DEECE66D) AND (2^48 - 1)

Trạng thái kế tiếp =
    (trạng thái hiện tại * 0x5DEECE66D + 0xB) mod 2^48

Kết quả đọc ra từ các bit cao của trạng thái mới.
~~~

Trong đặc tả `Random`, các chi tiết cập nhật trạng thái và cách phương thức lõi `next(int bits)` lấy bit được quy định, giúp chuỗi với cùng seed và cùng cách gọi có thể tái lập giữa những triển khai Java tương thích của **chính lớp này**.

Không nên tự triển khai LCG chỉ để gọi `nextInt`; hãy dùng API chuẩn. Nhưng hiểu cơ chế giúp giải thích một số tính chất:

- Cùng seed thiết lập cùng trạng thái ban đầu, nên cùng đường đi trạng thái nếu cùng cách tiêu thụ.
- Mỗi phương thức có thể tiêu thụ một hoặc nhiều lần cập nhật trạng thái.
- Seed đưa vào constructor có kiểu `long`, nhưng trạng thái cốt lõi chỉ có 48 bit. Hai seed chỉ khác ở các bit nằm ngoài 48 bit thấp có thể cho **cùng chuỗi**. Ví dụ `42L` và `42L + (1L << 48)` tạo cùng trạng thái ban đầu của `Random` chuẩn.
- Vì có không gian trạng thái hữu hạn và thuật toán có thể dự đoán, đây **không phải** bộ sinh thích hợp để tạo token bảo mật.

Khi nói “chuỗi cố định”, cần hiểu đó là **chuỗi thuật toán của một bộ sinh cụ thể**; cùng số seed đưa vào một thuật toán khác (ví dụ `SplittableRandom`) không có nghĩa cho các con số giống `Random`.

## 5. Thứ tự lời gọi quan trọng như seed

Một đối tượng `Random` có trạng thái biến đổi. Cách tiêu thụ nó ảnh hưởng các giá trị xuất hiện về sau.

~~~java
Random a = new Random(42L);
Random b = new Random(42L);

int aFirst = a.nextInt(100); // 30
int aSecond = a.nextInt(100); // 63

int bFirst = b.nextInt(100); // 30
// Nếu b được gọi thêm ở chỗ khác, các phép lấy sau đó sẽ
// không còn ứng với vị trí aSecond theo cách gọi này.
~~~

Lưu ý về các phương thức khác nhau:

| Phương thức | Ý nghĩa | Lưu ý về chuỗi |
| --- | --- | --- |
| `nextInt()` | Sinh một `int` từ toàn miền int | Tiêu thụ trạng thái |
| `nextInt(bound)` | Sinh `int` trong `[0, bound)` | Với bound không phải lũy thừa 2 có thể cần lấy lại để tránh độ lệch phân bố |
| `nextLong()` | Sinh một `long` | Thường kết hợp nhiều lần sinh bit bên trong |
| `nextDouble()` | Sinh `double` trong `[0.0, 1.0)` | Tiêu thụ các bit để tạo độ chính xác cho double |
| `nextBoolean()` | Sinh `true` hoặc `false` | Tiêu thụ trạng thái |
| `nextGaussian()` | Sinh giá trị phân bố chuẩn | Có cơ chế giữ lại một giá trị gaussian cho lần gọi sau, nên lịch sử gọi còn quan trọng hơn |

`nextInt(bound)` không nên được coi là **luôn** tiêu thụ đúng một bước trạng thái trong mọi trường hợp: thuật toán có thể phải thử lại nếu giá trị ứng viên nằm ngoài miền chấp nhận. `nextDouble()`, `nextLong()` và `nextGaussian()` cũng không được hiểu như cùng một lời gọi `nextInt()`.

Do đó, việc thay một `nextInt(100)` bằng `nextDouble()`, thêm một lời gọi debug, hoặc chuyển vị trí lấy số ngẫu nhiên trong chương trình có thể khiến **phần chuỗi tiếp theo khác với bản chạy trước** ngay cả khi seed giữ nguyên.

## 6. Seed giống nhau nhưng lời gọi khác: minh họa trạng thái

Giả sử một trò chơi sử dụng một `Random(42)` cho mọi quyết định. Bản đầu tiên:

~~~java
Random random = new Random(42L);
int enemyMove = random.nextInt(100);
int lootRoll = random.nextInt(100);
~~~

Bản sau có thêm thao tác lấy số trước khi tính `lootRoll`:

~~~java
Random random = new Random(42L);
int enemyMove = random.nextInt(100);
int weatherRoll = random.nextInt(100);
int lootRoll = random.nextInt(100);
~~~

Với ví dụ cụ thể này, `enemyMove` vẫn là `30`; `lootRoll` trước đó là `63`, còn sau khi thêm `weatherRoll` thì `lootRoll` là `48`. Không có lỗi ở `Random`: hai lần chạy đã **tiêu thụ chuỗi ở các vị trí khác nhau**.

Để mô phỏng nhiều phần tương đối độc lập (ví dụ thời tiết và chiến lợi phẩm), có thể cấp mỗi phần một `Random` riêng với seed được quản lý rõ ràng. Khi đó việc gọi thêm số ở phần thời tiết sẽ không tiến trạng thái của bộ sinh ở phần chiến lợi phẩm.

~~~java
Random weatherRandom = new Random(1001L);
Random lootRandom = new Random(2002L);
~~~

Các seed ở đây là ví dụ được chọn trước để dễ tái lập; không phải công thức bảo đảm hai chuỗi hoàn toàn độc lập về thống kê. Với bài toán mô phỏng lớn, hãy chọn chiến lược chia luồng sinh số và thuật toán phù hợp, đồng thời lưu rõ seed mỗi luồng.

## 7. setSeed(): phát lại từ đầu, không đảo ngược một bước

Một đối tượng đã phát nhiều giá trị có thể được khởi tạo lại:

~~~java
Random random = new Random(42L);

int first = random.nextInt(100);  // 30
int second = random.nextInt(100); // 63

random.setSeed(42L);

int replayFirst = random.nextInt(100); // 30
~~~

`setSeed(42L)` không “trừ một bước” từ trạng thái hiện tại. Nó **đặt lại trạng thái từ seed được cung cấp**. Điều đó có nghĩa là những lời gọi đã tiêu thụ trước đó không còn quyết định chuỗi sau khi reseed.

Riêng `Random.nextGaussian()` sử dụng bộ đệm để lưu một kết quả gaussian chưa trả ra; gọi `setSeed()` cũng xóa trạng thái bộ đệm này theo hợp đồng của lớp. Điều này quan trọng nếu cần replay chuỗi gaussian từ đầu.

Không nên gọi `setSeed` giữa các lần sinh số chỉ để “tăng độ ngẫu nhiên”. Reseed theo cùng giá trị liên tục sẽ làm chuỗi lặp lại từ đầu.

## 8. Seed do mình chọn và seed mặc định

Hai cách khởi tạo thường gặp:

~~~java
Random reproducible = new Random(2026L);
Random automatic = new Random();
~~~

**`new Random(2026L)`** dùng seed công khai, cố định do chương trình chọn. Khi thử nghiệm cần tái lập, hãy ghi lại seed và cách tiêu thụ các số.

**`new Random()`** dùng cơ chế seed khởi tạo tự động của Java. Hai lần tạo đối tượng có thể có seed khác nhau và chuỗi khác nhau, nhưng chương trình không nên dựa vào đó như một bảo đảm về bí mật, tính duy nhất tuyệt đối hoặc chất lượng mật mã. Nếu không ghi lại các điều kiện để tạo lại trạng thái, sẽ khó phát lại đúng một lần chạy.

Dùng `System.currentTimeMillis()` làm seed có thể tiện khi chạy thí nghiệm nhanh, nhưng nhiều lần tạo bộ sinh trong cùng một mili giây có thể nhận cùng seed. Với kiểm thử tự động, một seed cố định thường dễ điều tra lỗi hơn.

Một thực hành hữu ích là **in hoặc lưu seed của lần chạy gây lỗi**, kèm mã phiên bản và các tùy chọn ảnh hưởng số lần gọi. Khi lỗi chỉ xảy ra ở một kịch bản ngẫu nhiên, chạy lại với cùng dữ liệu giúp tái hiện.

## 9. Sinh số trong khoảng và tránh các bẫy

`random.nextInt(bound)` yêu cầu `bound > 0` và trả số trong khoảng nửa mở `[0, bound)`. Nếu `bound <= 0`, phương thức ném `IllegalArgumentException`.

~~~java
Random random = new Random(42L);
int die = random.nextInt(6) + 1; // 1..6
~~~

Muốn lấy số nguyên từ `min` đến `max` **bao gồm cả hai đầu** và biết chắc phép tính không tràn int:

~~~java
int value = min + random.nextInt(max - min + 1);
~~~

Đây chỉ là **mẫu biểu thức** khi `min`, `max` và độ rộng khoảng hợp lệ, hiệu `max - min + 1` nằm trong miền `int`. Với khoảng rộng, có thể dùng API nhận origin/bound phù hợp trên Java hiện đại hoặc tính bằng `long`, và phải kiểm tra biên trên có tràn hay không.

Java 17 cung cấp các API từ `RandomGenerator`, nhưng đừng tự động suy ra rằng một lời gọi API khác sẽ tiêu thụ trạng thái đúng như công thức `nextInt(bound)` cũ. Muốn replay chính xác, nên giữ cố định cách gọi và phiên bản chương trình.

Một điều nữa: seed cố định không biến năm số đầu thành “mẫu đều” trên miền 0..99. Tính tái lập của chuỗi và đánh giá phân bố thống kê là hai bài toán khác nhau. Không đánh giá tính đúng của PRNG bằng cách đòi mỗi số trong một đoạn ngắn xuất hiện đúng một lần.

## 10. Random, ThreadLocalRandom, SplittableRandom và SecureRandom

Các lớp liên quan giải quyết những nhu cầu khác nhau.

| Lớp/API | Tình huống thường dùng | Lưu ý với seed |
| --- | --- | --- |
| `java.util.Random` | Ví dụ, kiểm thử và mô phỏng đơn giản cần tái lập | Nhận seed tường minh; chuỗi thuật toán `Random` truyền thống được quy định |
| `java.util.SplittableRandom` | Sinh số cho tác vụ mô phỏng có thể chia nhỏ | Có constructor nhận seed, nhưng **thuật toán/chuỗi khác `Random`** |
| `java.util.concurrent.ThreadLocalRandom` | Sinh số thuận tiện trong nhiều luồng | Lấy bằng `current()`; không hỗ trợ `setSeed` bên ngoài để replay như `Random` |
| `java.security.SecureRandom` | Token, salt, nonce và dữ liệu cần tính khó dự đoán | Dành cho mục đích an toàn mật mã; cách seed/thuật toán phụ thuộc triển khai, không dùng như công cụ replay chuỗi `Random` |

`Random` có thể được dùng bởi nhiều luồng, nhưng **thứ tự thực tế của các lời gọi** từ các luồng có thể thay đổi do lập lịch. Vì vậy **cùng seed không đủ bảo đảm mỗi luồng nhận đúng cùng dãy giá trị** nếu các luồng chia sẻ một bộ sinh và chạy xen kẽ khác nhau.

`ThreadLocalRandom.current().setSeed(...)` không phải cách đặt seed hợp lệ cho trình sinh số của luồng: `setSeed` được thiết kế để ném `UnsupportedOperationException`. Với yêu cầu tái lập độc lập từng luồng, thiết kế phân chia seed/bộ sinh cụ thể cho từng nhiệm vụ; cân nhắc `SplittableRandom` hoặc bộ sinh từ `java.util.random` theo hợp đồng dự án.

**Tuyệt đối không sử dụng `new Random(fixedSeed)` để tạo token đăng nhập, khóa, OTP hay giá trị bí mật.** Các giá trị đầu ra có thể dự đoán vì thuật toán và seed không được bảo vệ cho nhu cầu này. Dùng `SecureRandom` và áp dụng hướng dẫn bảo mật phù hợp.

## 11. Streams, số lần tiêu thụ và tái lập

`Random` còn hỗ trợ tạo stream số nguyên:

~~~java
Random random = new Random(42L);

List<Integer> values = random.ints(5, 0, 100)
        .boxed()
        .toList();
~~~

Trong Java 17, `toList()` của Stream trả danh sách theo encounter order của stream có thứ tự. Ở ví dụ trên, ta tạo năm số nguyên trong `[0, 100)` từ bộ sinh được đặt seed.

Điểm quan trọng: stream tiêu thụ chính đối tượng `random`. Nếu gọi thêm `random.nextInt()` trước khi xử lý stream, kết quả lấy được có thể thay đổi. Nếu cần kết quả kiểm thử dễ đối chiếu, tiêu thụ stream theo thứ tự tuần tự và cố định những lời gọi ngoài stream.

Không nên giả định rằng chia sẻ một bộ sinh giữa các stream song song, các task đa luồng hoặc thay đổi cấu trúc pipeline sẽ giữ nguyên ánh xạ số ngẫu nhiên đến từng phần tử. Kết quả phụ thuộc cách tiêu thụ, và lịch trình thực thi có thể không được xác định.

## 12. Kiểm thử tái lập với một seed cố định

Với một thuật toán dùng số ngẫu nhiên, thay vì rải các lời gọi `new Random()` khắp nơi, có thể nhận một `Random` như tham số:

~~~java
static int rollDie(Random random) {
    return random.nextInt(6) + 1;
}
~~~

Đây là **mảnh phương thức** trong lớp Java có `import java.util.Random;`.

Ưu điểm: mã gọi kiểm soát được seed và trạng thái, giúp kiểm thử tái lập. Ví dụ:

~~~java
Random experiment = new Random(500L);
int firstRoll = rollDie(experiment);
int secondRoll = rollDie(experiment);
~~~

Nếu cần phát lại, tạo một `Random(500L)` mới và gọi `rollDie` hai lần cùng thứ tự. Để so sánh hai triển khai, có thể tạo **hai đối tượng riêng** có cùng seed, rồi đảm bảo cả hai triển khai tiêu thụ cùng số lần và kiểu lời gọi. Nếu thuật toán mới có thêm một lần rút số, phải xác định đó là thay đổi có chủ ý trước khi so sánh kết quả từng bước.

Khi ghi log để tái lập lỗi, những thông tin hữu ích thường gồm: **seed**, **lớp/thuật toán PRNG**, **phiên bản mã**, **tham số sinh số (như bound)** và **trình tự các sự kiện tiêu thụ**. Với chạy song song, nên bổ sung cách phân phối seed và thứ tự xử lý công việc.

## 13. Lỗi thường gặp

**Lỗi 1 — Tạo `new Random(42L)` trong mỗi vòng lặp.** Mỗi đối tượng khởi đầu tại vị trí đầu chuỗi, nên gọi `nextInt(100)` đầu tiên đều được `30`. Đúng hơn là khởi tạo một đối tượng ngoài vòng lặp rồi sử dụng liên tục.

**Lỗi 2 — Nghĩ seed giống nhau là đủ.** Nếu một nhánh code thực hiện thêm `nextInt`, `nextLong`, `nextDouble`, hoặc `nextGaussian`, các vị trí tiêu thụ không còn tương ứng; cùng seed cũng không bảo đảm kết quả ở bước nghiệp vụ tương ứng giống nhau.

**Lỗi 3 — Dùng `nextInt(10)` rồi kỳ vọng có số 10.** Bound là **exclusive**. `nextInt(10)` chỉ trả 0..9. Để sinh xúc xắc 1..6, dùng `nextInt(6) + 1`.

**Lỗi 4 — Cho bound bằng 0 hoặc âm.** `nextInt(0)` và `nextInt(-3)` ném `IllegalArgumentException`; phải kiểm tra điều kiện đầu vào.

**Lỗi 5 — Trộn kết quả nhiều luồng và mong replay y nguyên.** Cùng seed cho một đối tượng chia sẻ không cố định thứ tự xen kẽ giữa các luồng. Thiết kế một bộ sinh cho mỗi tác vụ với seed quản lý độc lập khi cần tái lập.

**Lỗi 6 — Dùng `Random` cho mã bảo mật.** Seed có thể bị suy đoán và trạng thái LCG chỉ có 48 bit; dùng `SecureRandom` cho giá trị cần khó đoán.

**Lỗi 7 — Chỉ lưu seed nhưng không lưu thuật toán hoặc thứ tự tiêu thụ.** Khi đổi loại PRNG, thay đổi bound hay thêm lần gọi, dãy số có thể khác. Log cần có đủ ngữ cảnh để tái hiện.

**Lỗi 8 — Giả định mọi seed `long` đều sinh một chuỗi duy nhất.** Đối với `java.util.Random` chuẩn, seed được biến đổi rồi giới hạn về 48 bit; có những seed `long` khác nhau cho cùng chuỗi.

**Lỗi 9 — Đồng nhất “giống chuỗi” với “đảm bảo phân bố tốt trong mọi tình huống”.** Tái lập không chứng minh tính đều hay tính độc lập thống kê. Nếu kết quả mô phỏng đòi hỏi chất lượng thống kê cao, lựa chọn thuật toán phải được đánh giá riêng.

## 14. Bài luyện tập tự kiểm tra

**Câu 1.** Chương trình sau in gì?

~~~java
Random a = new Random(42L);
Random b = new Random(42L);

System.out.println(a.nextInt(100));
System.out.println(b.nextInt(100));
System.out.println(a.nextInt(100));
~~~

**Đáp án:**

~~~text
30
30
63
~~~

Mỗi đối tượng giữ trạng thái riêng. Hai lời gọi đầu lấy số đầu tiên của hai chuỗi giống nhau; lời gọi cuối là số thứ hai của `a`.

**Câu 2.** Nếu thêm một lời gọi `nextInt(100)` vào trước phần tính kết quả của một mô phỏng, seed có đổi không? Kết quả về sau có thể đổi không?

**Đáp án:** Seed ban đầu không đổi, nhưng trạng thái đã tiến thêm. Vì vậy những số mà các bước sau nhận được có thể thay đổi.

**Câu 3.** Hai `Random` được tạo bằng `new Random(42L)` và `new Random(42L + (1L << 48))` có cho cùng chuỗi `nextInt(100)` không?

**Đáp án:** Có, đối với thuật toán 48 bit của `java.util.Random` được mô tả ở trên: phần chênh lệch nằm ngoài 48 bit thấp nên trạng thái ban đầu tương đương.

**Câu 4.** Muốn tái hiện một bug chỉ xảy ra với dữ liệu giả ngẫu nhiên, tối thiểu cần ghi gì ngoài seed?

**Đáp án:** Bộ sinh và phiên bản/hành vi chương trình, cùng những phương thức và tham số tiêu thụ chuỗi theo đúng thứ tự. Nếu có song song, cần kiểm soát việc phân chia chuỗi và lịch trình hoặc ghi thông tin đủ để tái hiện.

**Câu 5.** Có nên dùng `Random(42L)` để tạo mã xác thực 6 chữ số cho người dùng không?

**Đáp án:** Không. Seed cố định dẫn đến chuỗi có thể tái lập và `Random` không phải bộ sinh mật mã. Hãy dùng `SecureRandom` cho trường hợp cần khó đoán.

**Ghi nhớ:** Seed là điểm khởi đầu của một **chuỗi giả ngẫu nhiên có trạng thái**. Với `java.util.Random`, cùng seed, cùng cách gọi và cùng thứ tự tiêu thụ cho cùng kết quả; `setSeed` khởi tạo lại chuỗi. Dùng đặc tính này để tái lập kiểm thử/mô phỏng, đồng thời nhớ giới hạn về đa luồng, thay đổi thuật toán và bảo mật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-003">Math.random và giới hạn sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Math.random và giới hạn sử dụng trong Java

## 1. Math.random() là gì?

`Math.random()` là phương thức `static` của `java.lang.Math`, dùng để lấy **một giá trị `double` giả ngẫu nhiên** (pseudorandom) trong khoảng từ `0.0` đến dưới `1.0`. Cú pháp đơn giản, không cần import:

```java
double value = Math.random();
```

Hợp đồng giá trị trả về:

```text
0.0 <= value < 1.0
```

Nghĩa là **0.0 thuộc khoảng**, còn **1.0 không thuộc khoảng**. Các giá trị được chọn giả ngẫu nhiên, phân bố *xấp xỉ đồng đều* trong khoảng này. Mỗi lần gọi lấy một giá trị mới từ chuỗi số do bộ sinh tạo ra; không có bảo đảm rằng các giá trị liên tiếp sẽ khác nhau.

Ví dụ, trong một lần chạy, kết quả *có thể* giống:

```text
0.182...
0.893...
0.407...
```

Đây chỉ là ví dụ minh họa hình dạng dữ liệu, **không phải stdout cố định**. Không viết test dựa trên các số cụ thể đó.

`Math.random()` thường phù hợp với các bài tập mô phỏng đơn giản, chọn ngẫu nhiên để hiển thị hoặc các trò chơi nhỏ **không có yêu cầu bảo mật**. Sử dụng đúng cũng cần hiểu giới hạn của nó: khoảng giá trị, độ chính xác `double`, cách chuyển thành số nguyên, khả năng tái lập kết quả, chất lượng ngẫu nhiên và chi phí khi gọi từ nhiều luồng.

## 2. Hành vi của bộ sinh và ý nghĩa “giả ngẫu nhiên”

Theo hợp đồng Java 17, lần gọi `Math.random()` đầu tiên khởi tạo một bộ sinh số giả ngẫu nhiên như thể tạo một đối tượng `new java.util.Random()`; những lời gọi tiếp theo dùng chung bộ sinh đó.

```text
Math.random() ─┐
Math.random() ─┼──> bộ sinh Random dùng chung ──> double trong [0.0, 1.0)
Math.random() ─┘
```

Một bộ sinh giả ngẫu nhiên tạo ra chuỗi số theo thuật toán và trạng thái nội bộ. Nhìn từ ngoài, chuỗi có thể hữu ích cho thống kê và mô phỏng, nhưng nó không tương đương với nguồn ngẫu nhiên bí mật hoặc ngẫu nhiên vật lý. Không được suy ra rằng một số “khó đoán bằng mắt” thì phù hợp làm mã xác thực.

Các hệ quả quan trọng:

- **Không truyền seed cho `Math.random()`:** phương thức không có tham số và không cho người gọi đặt hay đọc seed của bộ sinh dùng chung. Bạn không thể yêu cầu “chạy lại đúng dãy vừa rồi” bằng API này.
- **Trạng thái được chia sẻ:** nhiều lời gọi ở các phần khác nhau của ứng dụng cùng tiêu thụ chuỗi từ bộ sinh bên trong. Việc gọi thêm ở một nơi có thể thay đổi số mà nơi khác nhận được.
- **An toàn khi có nhiều luồng gọi:** Java đồng bộ hóa việc sử dụng đúng cách. Tuy nhiên, nhiều luồng gọi với tốc độ rất cao có thể gặp cạnh tranh truy cập bộ sinh dùng chung và giảm hiệu năng.
- **Không an toàn cho bảo mật:** thuật toán `Random` thông thường không nhằm chống dự đoán hoặc tấn công mật mã.

Các đặc điểm này khiến `Math.random()` rất tiện cho ví dụ ngắn, nhưng kém phù hợp khi hệ thống cần kiểm soát nguồn ngẫu nhiên, quản lý seed, chạy song song quy mô lớn hoặc tạo token bảo mật.

## 3. Tạo số nguyên bằng Math.random()

Muốn mô phỏng xúc xắc sáu mặt từ 1 đến 6, một cách thường gặp là:

```java
int dice = (int) (Math.random() * 6) + 1;
```

Giải thích từng bước:

1. `Math.random()` trả `r` với `0.0 <= r < 1.0`.
2. `r * 6` có giá trị từ `0.0` đến dưới `6.0`.
3. Ép `(int)` với một số hữu hạn không âm sẽ bỏ phần thập phân, cho kết quả từ `0` đến `5`.
4. Cộng `1` được số nguyên từ `1` đến `6`.

Dấu ngoặc quanh phép nhân **rất quan trọng**. So sánh:

```java
int correct = (int) (Math.random() * 10); // 0..9
int wrong   = (int) Math.random() * 10;   // luôn là 0
```

Ở dòng sai, `(int) Math.random()` luôn bằng `0`, vì kết quả gốc nhỏ hơn `1.0`, rồi mới nhân `10`. Java không tự điều chỉnh thứ tự phép tính theo ý định người viết.

Để tạo số nguyên trong `[min, max]` (*hai đầu đều bao gồm*), công thức phổ biến cho **khoảng nhỏ, hợp lệ** là:

```java
int value = min + (int) (Math.random() * (max - min + 1));
```

Ví dụ `min = 5`, `max = 9` sẽ cho các số 5, 6, 7, 8, 9. Nhưng công thức này có những **giới hạn quan trọng**:

- `max - min + 1` được tính bằng `int` trong ví dụ. Với khoảng lớn, phép tính có thể **tràn số nguyên** trước khi nhân với `Math.random()`.
- Phép đổi từ `double` sang `int` và phép nhân dấu phẩy động có giới hạn độ chính xác. Với số lượng kết quả rất lớn hoặc nhu cầu phân bố đều nghiêm ngặt, cách nhân rồi ép kiểu không phải lựa chọn tốt.
- Phương thức tạo `double` từ một tập giá trị rời rạc hữu hạn; chia số lượng trạng thái đó cho các khoảng nguyên có thể có độ chênh xác suất rất nhỏ. Với yêu cầu lấy số nguyên đồng đều, nên ưu tiên API sinh số nguyên theo khoảng thay vì tự biến đổi từ `double`.
- Nếu `min > max`, đầu vào không hợp lệ; không nên âm thầm tính tiếp.

Đối với số nguyên trong đoạn `[min, max)` (*bao gồm min, không bao gồm max*), `ThreadLocalRandom.current().nextInt(min, max)` hoặc một `RandomGenerator.nextInt(min, max)` làm rõ ý nghĩa giới hạn và xử lý việc lấy giá trị nguyên một cách phù hợp hơn. Với `max == Integer.MAX_VALUE`, đừng vô tình tính `max + 1` bằng `int` để cố tạo khoảng đóng; hãy chọn overload/ràng buộc khác phù hợp.

## 4. Lỗi off-by-one: biên bao gồm và loại trừ

Điều thường khiến code tạo số ngẫu nhiên sai là nhầm `[a, b]` với `[a, b)`.

| Yêu cầu | Cách dùng `Math.random()` cho ví dụ nhỏ | Kết quả |
| --- | --- | --- |
| Số nguyên từ 0 đến 9 | `(int) (Math.random() * 10)` | 0..9 |
| Số nguyên từ 1 đến 10 | `1 + (int) (Math.random() * 10)` | 1..10 |
| Xúc xắc sáu mặt | `1 + (int) (Math.random() * 6)` | 1..6 |
| Hai lựa chọn 0 hoặc 1 | `(int) (Math.random() * 2)` | 0 hoặc 1 |
| Xác suất xấp xỉ 25% | `Math.random() < 0.25` | `boolean` |
| Một số thực trong `[0, 1)` | `Math.random()` | `double` |

Trong điều kiện `Math.random() < 0.25`, kết quả `true` có xác suất xấp xỉ 25% theo mô hình phân bố đã nêu. Một số lần chạy ít ỏi không có lý do gì phải cho đúng 25% số lần `true`.

Các lỗi phổ biến khác:

```java
int a = (int) (Math.random() * 6);      // 0..5, không phải 1..6
int b = (int) (Math.random() * 6) + 1;  // 1..6

int c = Math.round((float) (Math.random() * 6)); // 0..6,
                                                  // xác suất ở hai biên khác phần giữa
```

`Math.round` làm tròn về số nguyên gần nhất, khiến khoảng giá trị gần hai đầu chỉ chiếm xấp xỉ một nửa chiều rộng so với các khoảng giữa; vì thế **không** phải cách thay thế tương đương cho `floor` khi muốn các số nguyên có xác suất gần như nhau.

Không nên kiểm tra `Math.random() == 1.0`: điều kiện ấy luôn `false` theo hợp đồng API. Nếu muốn cho phép số ở **cận trên**, hãy xác định rõ bài toán là số nguyên hay số thực. Với đoạn số thực đóng, công thức chuyển đổi đơn giản `min + Math.random() * (max - min)` cũng không đảm bảo mọi giá trị biên có xác suất như nhau; và làm tròn dấu phẩy động có thể ảnh hưởng cận. Đừng hứa hẹn tính chất toán học mà biểu diễn hữu hạn không bảo đảm.

## 5. Ví dụ hoàn chỉnh Java 17+: kiểm tra khoảng mà không đoán số ngẫu nhiên

Lưu tệp `MathRandomDemo.java`:

```java
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class MathRandomDemo {
    private static int rollWithMath() {
        return 1 + (int) (Math.random() * 6);
    }

    private static boolean validDie(int value) {
        return value >= 1 && value <= 6;
    }

    public static void main(String[] args) {
        double sample = Math.random();
        System.out.println("Math.random in [0,1): "
                + (sample >= 0.0 && sample < 1.0));

        int die = rollWithMath();
        System.out.println("Math dice in [1,6]: " + validDie(die));

        int threadedDie = ThreadLocalRandom.current().nextInt(1, 7);
        System.out.println("ThreadLocalRandom dice in [1,6]: "
                + validDie(threadedDie));

        Random first = new Random(12345L);
        Random second = new Random(12345L);
        boolean sameSequence = true;
        for (int i = 0; i < 100; i++) {
            if (first.nextInt(6) != second.nextInt(6)) {
                sameSequence = false;
            }
        }
        System.out.println("Same seed, same sequence: " + sameSequence);

        SecureRandom secure = new SecureRandom();
        byte[] tokenBytes = new byte[16];
        secure.nextBytes(tokenBytes);
        System.out.println("Secure byte length: " + tokenBytes.length);
    }
}
```

Chạy:

```bash
javac MathRandomDemo.java
java MathRandomDemo
```

Stdout **ổn định về các điều kiện được in**, dù những giá trị được sinh bên trong thay đổi giữa các lần chạy:

```text
Math.random in [0,1): true
Math dice in [1,6]: true
ThreadLocalRandom dice in [1,6]: true
Same seed, same sequence: true
Secure byte length: 16
```

Bài học của ví dụ: khi kiểm tra giá trị giả ngẫu nhiên, hãy kiểm tra những thuộc tính có bảo đảm (kiểu dữ liệu, phạm vi, cấu trúc, khả năng tái lập với seed xác định). Đừng assert một giá trị cố định từ `Math.random()`.

`SecureRandom` chỉ được dùng để minh họa cách tạo byte phù hợp cho trường hợp nhạy cảm về an toàn; 16 byte có đủ cho một ứng dụng cụ thể hay không phụ thuộc vào thiết kế token, entropy yêu cầu, thời hạn và cơ chế chống phát lại.

## 6. Hạn chế về khả năng tái lập và kiểm thử

Trong mô phỏng, lập trình viên thường muốn chạy **cùng đầu vào và cùng seed thì nhận cùng chuỗi ngẫu nhiên** để gỡ lỗi và so sánh kết quả. `Math.random()` không nhận seed và dùng trạng thái ẩn dùng chung, nên khó kiểm soát theo từng test.

Thay bằng đối tượng `java.util.Random` có seed được truyền từ bên ngoài:

```java
import java.util.Random;

public class SeededRandomDemo {
    public static void main(String[] args) {
        Random a = new Random(42L);
        Random b = new Random(42L);

        boolean same = true;
        for (int i = 0; i < 1000; i++) {
            same &= a.nextInt(100) == b.nextInt(100);
        }
        System.out.println("Repeatable: " + same);
    }
}
```

Chạy `javac SeededRandomDemo.java` và `java SeededRandomDemo`, stdout:

```text
Repeatable: true
```

Hai đối tượng `Random` độc lập với cùng seed và **cùng thứ tự lời gọi** tạo cùng chuỗi. Nếu một đối tượng gọi `nextInt` thêm một lần, dãy tiếp theo sẽ bị lệch so với đối tượng còn lại. Seed có ý nghĩa để tái hiện test, **không phải** để bảo mật.

Với thiết kế có thể test, nên truyền nguồn sinh số vào đối tượng nghiệp vụ (dependency injection) thay vì gọi `Math.random()` trực tiếp ở mọi nơi:

```java
import java.util.Objects;
import java.util.random.RandomGenerator;

public class DiceService {
    private final RandomGenerator rng;

    public DiceService(RandomGenerator rng) {
        this.rng = Objects.requireNonNull(rng);
    }

    public int roll() {
        return rng.nextInt(1, 7);
    }
}
```

Đây là class có thể biên dịch trên Java 17+, không có `main`. Để chạy ứng dụng, truyền một instance như `new DiceService(new java.util.Random(42L))`. Khi cần triển khai khác, người gọi có thể truyền generator khác tương thích với `RandomGenerator`. Việc test không còn phụ thuộc vào bộ sinh ẩn của `Math.random()`.

`RandomGenerator` là interface chuẩn từ Java 17, giúp tách mã nghiệp vụ khỏi thuật toán sinh số. Nếu yêu cầu **tái hiện tuyệt đối trong môi trường khác nhau**, hãy ghi rõ loại bộ sinh, seed, thứ tự sử dụng và phiên bản thực thi; tránh giả định mọi thuật toán hoặc mọi phiên bản đều cho cùng chuỗi chỉ vì seed giống nhau.

## 7. Giới hạn khi dùng nhiều luồng

`Math.random()` dùng một bộ sinh chia sẻ. Java bảo đảm gọi được an toàn từ nhiều luồng, nên bạn **không phải tự khóa** cho mỗi lần gọi. Nhưng với tác vụ có tần suất sinh ngẫu nhiên cao từ nhiều luồng, việc chia sẻ có thể dẫn tới cạnh tranh và làm chậm.

Với workload song song không nhạy cảm về bảo mật, `ThreadLocalRandom` thường phù hợp hơn:

```java
import java.util.concurrent.ThreadLocalRandom;

int id = ThreadLocalRandom.current().nextInt(1000, 2000);
double unit = ThreadLocalRandom.current().nextDouble();
```

Hai đoạn lệnh trên chỉ là **minh họa ở bên trong phương thức hoặc lớp**, không phải Java file chạy độc lập. `nextInt(1000, 2000)` cho các giá trị từ 1000 đến 1999; 2000 là biên **không bao gồm**. `nextDouble()` cũng cho số trong `[0.0, 1.0)`.

Lợi ích và giới hạn:

- Bộ sinh dành cho mỗi luồng giúp tránh phần lớn tranh chấp với một `Random` dùng chung.
- Không có cơ chế thiết lập seed cho `ThreadLocalRandom.current()` bằng API thông thường. Vì vậy nó không thích hợp nếu muốn tự điều khiển dãy bằng seed trong test.
- `ThreadLocalRandom` cũng **không phải nguồn ngẫu nhiên bảo mật**.
- Trong các mô phỏng song song yêu cầu tái lập, có thể cấp generator riêng cho mỗi task với seed được quản lý rõ ràng, hoặc nghiên cứu `SplittableRandom`/`RandomGenerator` phù hợp. Cần quy định việc chia các dòng ngẫu nhiên theo task để tránh thay đổi kết quả khi thay lịch luồng.

Việc dùng nhiều luồng tự nó không làm `Math.random()` sinh ra giá trị ngoài khoảng; giới hạn chính ở đây là khả năng kiểm soát và hiệu năng khi cạnh tranh cao.

## 8. Giới hạn bảo mật: tuyệt đối không tạo bí mật bằng Math.random()

Một chuỗi số khó đoán khi quan sát vài lần **không đồng nghĩa** khó dự đoán với người tấn công. `Math.random()` và `Random` được thiết kế cho mục đích giả ngẫu nhiên thông thường, không có bảo đảm về sức mạnh mật mã.

Không dùng `Math.random()` để tạo:

- Mã OTP, mã xác nhận giao dịch, mã reset mật khẩu.
- Session token, API key, access token, nonce phục vụ bảo mật.
- Khóa mã hóa, salt yêu cầu sinh ngẫu nhiên an toàn hoặc dữ liệu liên quan tới kiểm soát truy cập.
- Giá trị có thể mang lợi ích kinh tế cho kẻ đoán đúng, nếu việc bảo vệ chống dự đoán là một phần yêu cầu.

Dùng `java.security.SecureRandom` cho mục đích mật mã. Ví dụ tạo 32 byte ngẫu nhiên:

```java
import java.security.SecureRandom;

public class SecureBytesDemo {
    public static void main(String[] args) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        System.out.println("Generated bytes: " + bytes.length);
    }
}
```

Chạy `javac SecureBytesDemo.java` rồi `java SecureBytesDemo`:

```text
Generated bytes: 32
```

Đoạn code không in các byte bí mật ra log. Khi chuyển byte thành token, cần dùng encoding an toàn (ví dụ Base64 URL-safe), quản lý vòng đời token và lưu trữ phù hợp. Tạo byte ngẫu nhiên an toàn là một phần của thiết kế bảo mật, không tự nó giải quyết xác thực hay phân quyền.

Không cố cải thiện `Math.random()` bằng cách nhân, làm tròn, ghép nhiều kết quả hoặc đặt lại seed của một `Random` khác. Các thao tác đó không biến nguồn giả ngẫu nhiên không bảo mật thành nguồn đáp ứng yêu cầu mật mã.

## 9. Giới hạn thống kê và số dấu phẩy động

Một `double` có độ chính xác nhị phân hữu hạn (thường được mô tả bằng 53 bit significand). Vì vậy, các kết quả từ `Math.random()` là một **tập rời rạc hữu hạn** những số trong khoảng `[0.0, 1.0)`, chứ không phải mọi số thực của toán học.

Ba hệ quả:

**Thứ nhất, phân bố chỉ xấp xỉ đồng đều.** Đối với mô phỏng thông thường điều này thường đủ tốt. Với thuật toán cần đặc tính thống kê cao, chu kỳ dài, các luồng độc lập hoặc yêu cầu được kiểm chứng riêng, cần chọn bộ sinh và thiết kế phép thử phù hợp, không mặc định `Math.random()` là tối ưu.

**Thứ hai, phép chuyển khoảng không tự bảo toàn mọi tính chất lý tưởng.** Khi nhân `Math.random()` với một số nguyên rồi ép `int`, cách ánh xạ từ các trạng thái `double` hữu hạn đến các số nguyên có thể tạo sai lệch phân bố nhỏ. Đối với khoảng nguyên, API `nextInt(bound)` hay `nextInt(origin, bound)` thích hợp hơn việc tự viết phép đổi khi cần tính đúng đắn và xử lý các biên.

**Thứ ba, nhân/cộng số thực có thể làm tròn hoặc tràn.** Ví dụ:

```java
double min = 0.0;
double max = 10.0;
double value = min + Math.random() * (max - min);
```

Trong trường hợp đơn giản này, `value` nằm trong `[0.0, 10.0)` theo cách hiểu thông thường. Nhưng với các số `double` rất lớn, rất gần nhau hoặc không hữu hạn, phép `max - min`, nhân và cộng có thể tràn, mất precision hoặc bị làm tròn tới cận trên. Tránh đem công thức giản đơn áp dụng vô điều kiện cho mọi dải `double`.

Nếu đang dùng Java 17+ và cần các số `double` trong khoảng, xem các API `nextDouble(origin, bound)` của `RandomGenerator` hoặc `ThreadLocalRandom`, đồng thời kiểm tra đầu vào theo hợp đồng của API. Khi bài toán yêu cầu tiền tệ hay phép tính decimal chính xác, không nên lấy `double` từ `Math.random()` rồi giả định phép tính sau đó không còn sai số nhị phân.

## 10. Chọn API sinh số ngẫu nhiên phù hợp

| API | Hợp lý khi | Lưu ý |
| --- | --- | --- |
| `Math.random()` | Ví dụ ngắn, mô phỏng đơn giản, không cần quản lý seed | Trả `double` trong `[0,1)`; seed ẩn, bộ sinh dùng chung; không bảo mật |
| `new Random(seed)` | Test có thể lặp lại, thuật toán muốn chủ động quản lý seed | Có trạng thái riêng; đồng seed + cùng lời gọi tạo cùng dãy; không bảo mật |
| `ThreadLocalRandom.current()` | Tạo số thường xuyên trong nhiều luồng | Ít tranh chấp, có API khoảng nguyên; không cho caller đặt seed; không bảo mật |
| `SplittableRandom` | Mô phỏng cần chia bộ sinh thành các luồng/task riêng | Có thể đặt seed; không an toàn để chia sẻ cùng instance giữa nhiều luồng; không bảo mật |
| `RandomGenerator` (Java 17+) | Muốn tách thuật toán sinh số khỏi nghiệp vụ; lựa chọn/đổi implementation | Interface, tự nó không bảo đảm an toàn mật mã hay thread safety |
| `SecureRandom` | Sinh byte/token hoặc giá trị nhạy cảm về bảo mật | Có bảo đảm mật mã theo provider/thuật toán; có thể tốn tài nguyên hơn |

Với `ThreadLocalRandom`, `Random`, `SplittableRandom`, `SecureRandom`, các overload và tính chất của từng API có thể khác nhau. Hãy dùng đúng phương thức thay vì giả định mọi lớp đều có cùng signature.

Nguồn tham khảo chính thức (Java 17):

- [Math — random()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html#random())
- [Random](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html)
- [ThreadLocalRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadLocalRandom.html)
- [SecureRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html)
- [java.util.random và RandomGenerator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/package-summary.html)

## 11. Kiểm thử đúng: test tính chất thay vì một mẫu cụ thể

Với `Math.random()`, tránh test kiểu:

```java
// Sai: không có bảo đảm sample == 0.5.
if (Math.random() != 0.5) {
    throw new AssertionError("Unexpected random value");
}
```

Nên kiểm tra đúng hợp đồng:

```java
double r = Math.random();
if (!(r >= 0.0 && r < 1.0)) {
    throw new AssertionError("Math.random out of range");
}
```

Đây là những đoạn lệnh ngắn đặt trong phương thức, không phải file độc lập. Với phép đổi số nguyên theo yêu cầu, kiểm tra cả cận dưới và cận trên. Khi phải khẳng định dãy kết quả cụ thể của thuật toán nghiệp vụ, hãy truyền một nguồn sinh số có thể điều khiển hoặc giả lập và test riêng phần tính toán.

Kiểm thử thống kê cần số mẫu và tiêu chí phù hợp; việc lấy 20 giá trị rồi thấy tỷ lệ mỗi số khác nhau không đủ để kết luận thuật toán sai. Ngược lại, vài mẫu trông có vẻ phân bố đều cũng không chứng minh bộ sinh thích hợp cho bảo mật.

## 12. Các sai lầm thường gặp

| Sai lầm | Điều gì xảy ra | Cách khắc phục |
| --- | --- | --- |
| Nghĩ `Math.random()` có thể trả `1.0` | Sai về cận trên | Ghi nhớ `0.0 <= r < 1.0` |
| Dùng `(int) Math.random() * n` | Luôn nhận 0 với `n` hữu hạn trong phép nhân nguyên | Dùng `(int) (Math.random() * n)` |
| Dùng `Math.round(Math.random() * n)` để lấy 0..n đồng đều | Các điểm biên có xác suất khác điểm trong | Với khoảng nguyên, dùng `nextInt` |
| Nhầm `nextInt(1, 6)` nghĩa là xúc xắc 1..6 | Chỉ tạo 1..5 | Dùng `nextInt(1, 7)` |
| Tính `max - min + 1` bằng `int` cho khoảng lớn | Có thể overflow | Dùng API khoảng hỗ trợ kiểu phù hợp và kiểm tra biên |
| Muốn test tái lập nhưng gọi `Math.random()` trực tiếp | Không thể kiểm soát seed/trình tự trên từng test | Truyền `Random` hoặc `RandomGenerator` có seed |
| Dùng `Math.random()` cho mã xác nhận/token | Có nguy cơ dự đoán, không đạt yêu cầu bảo mật | Dùng `SecureRandom` và thiết kế token an toàn |
| Một lượng lớn luồng cùng gọi `Math.random()` liên tục | Có thể tranh chấp bộ sinh dùng chung | Cân nhắc `ThreadLocalRandom` hoặc generator theo task |
| Suy từ một lần chạy rằng mọi kết quả đều xuất hiện đều | Nhầm hiện tượng ngẫu nhiên với đảm bảo tuyệt đối | Phân tích xác suất, chọn mẫu và tiêu chí thống kê phù hợp |
| Cho rằng nhân với 10 tạo đúng 10 giá trị `double` | Vẫn là `double` liên tục theo khoảng giá trị rời rạc hữu hạn | Ép nguyên đúng chỗ nếu cần giá trị nguyên |

## 13. Câu hỏi tự kiểm tra

1. **Khoảng của `Math.random()` là gì?** `[0.0, 1.0)`, nghĩa là có 0.0 nhưng không có 1.0.
2. **Vì sao `(int) Math.random() * 6` không mô phỏng xúc xắc?** Vì ép `int` trước phép nhân biến kết quả thành 0.
3. **Cách mô phỏng xúc xắc bằng `Math.random()` là gì?** `1 + (int) (Math.random() * 6)`, phù hợp bài tập nhỏ không cần chất lượng/khả năng bảo mật đặc biệt.
4. **Tại sao không dùng `Math.random()` để tạo password-reset token?** Vì nguồn `Random` bên trong không được thiết kế để chống dự đoán theo yêu cầu mật mã; nên dùng `SecureRandom`.
5. **Khi test muốn cùng seed cho ra cùng chuỗi, nên làm thế nào?** Dùng `new Random(seed)` hoặc một generator hỗ trợ seed, đảm bảo cùng thuật toán và cùng trình tự gọi.
6. **`ThreadLocalRandom.current().nextInt(1, 7)` có thể trả 7 không?** Không; biên trên 7 là exclusive, kết quả từ 1 đến 6.
7. **Nếu nhiều luồng cần hàng triệu số ngẫu nhiên cho mô phỏng, giới hạn nào của `Math.random()` cần lưu ý?** Trạng thái dùng chung có thể gây contention; cân nhắc generator theo luồng hoặc theo task.
8. **Một số `double` nhìn ngẫu nhiên có phải là đủ an toàn để tạo khóa bí mật?** Không; phân bố nhìn có vẻ tốt và bảo mật chống dự đoán là hai yêu cầu khác nhau.

## 14. Kết luận

`Math.random()` là công cụ ngắn gọn để lấy một `double` giả ngẫu nhiên trong `[0.0, 1.0)`. Nó phù hợp cho bài tập và mô phỏng nhỏ, nhưng có giới hạn do bộ sinh dùng chung, seed không kiểm soát được, tính rời rạc của `double`, rủi ro sai biên khi chuyển sang số nguyên và đặc biệt là **không bảo đảm an toàn mật mã**.

Hãy chọn API theo yêu cầu: `Random(seed)` cho test tái lập, `ThreadLocalRandom` cho tải song song thông thường, `RandomGenerator` để tách mã nghiệp vụ khỏi bộ sinh, và `SecureRandom` khi giá trị cần chống dự đoán. Khi chuyển đổi khoảng, phải định nghĩa chính xác **biên bao gồm/loại trừ** và test theo những thuộc tính API thực sự bảo đảm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-004">ThreadLocalRandom khi nhiều luồng sinh số</a>

<details>
<summary>Xem chi tiết</summary>

# `ThreadLocalRandom` khi nhiều luồng cùng sinh số ngẫu nhiên

## 1. Bài toán: nhiều tác vụ, nhiều lần sinh số

Một ứng dụng Java có thể đồng thời thực hiện nhiều phép mô phỏng, chọn thời gian chờ ngẫu nhiên (jitter), chia công việc vào các nhóm hoặc sinh dữ liệu thử nghiệm. Nếu mỗi tác vụ liên tục cần giá trị ngẫu nhiên, cách tổ chức bộ sinh có thể ảnh hưởng tới thông lượng và mức tranh chấp giữa các luồng.

Ví dụ một thread pool có bốn luồng, mỗi luồng mô phỏng 10.000 lần tung xúc xắc. Những câu hỏi cần trả lời:

- Làm thế nào để mỗi luồng lấy số từ `1` đến `6` mà không phải cùng cập nhật trạng thái của một đối tượng `Random` dùng chung?
- Làm thế nào để bảo đảm ranh giới khoảng số đúng, tránh truy cập chung một mảng đếm không được đồng bộ?
- Kết quả nào có thể kiểm thử một cách ổn định khi giá trị ngẫu nhiên thay đổi theo từng lần chạy?
- Khi nào không nên dùng `ThreadLocalRandom`, chẳng hạn khi cần seed tái lập chính xác hoặc giá trị bảo mật?

Lớp `java.util.concurrent.ThreadLocalRandom` xuất hiện từ Java 7 và vẫn thích hợp với Java 17+. Đây là **bộ sinh số giả ngẫu nhiên gắn với luồng đang thực thi**, hữu ích khi nhiều luồng cần sinh số thường xuyên.

## 2. `ThreadLocalRandom` hoạt động theo luồng như thế nào?

Sử dụng phổ biến:

```java
import java.util.concurrent.ThreadLocalRandom;

int number = ThreadLocalRandom.current().nextInt(1, 7);
// number thuộc {1, 2, 3, 4, 5, 6}.
```

`current()` trả đối tượng `ThreadLocalRandom` dành cho **luồng đang gọi**. Thông thường, mỗi worker tự gọi `current()` bên trong tác vụ đang chạy. Theo tài liệu API, các phương thức của đối tượng này **chỉ nên được gọi bởi chính luồng đã lấy nó**, không truyền đối tượng đó sang luồng khác.

```java
Runnable task = () -> {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int sample = rng.nextInt(100); // 0 <= sample < 100
    // Xử lý sample ngay trong worker.
};
```

Mã trên minh họa cách viết một `Runnable`; để thực thi cần gửi `task` vào một `Thread` hoặc `ExecutorService`. Có thể gọi `ThreadLocalRandom.current()` mỗi lần cần số, hoặc giữ biến `rng` *cục bộ trong phạm vi worker* để gọi nhiều lần trong cùng tác vụ.

### Vì sao thường thuận lợi hơn một `Random` dùng chung?

Một đối tượng `java.util.Random` có thể được nhiều luồng sử dụng, nhưng nhiều lời gọi cập nhật trạng thái của **cùng một bộ sinh**, từ đó có thể gây tranh chấp và tăng chi phí khi tải song song cao. `ThreadLocalRandom` tránh việc các luồng cùng cập nhật một bộ sinh `Random` chia sẻ như vậy; điều này thường giảm overhead và contention trong các tác vụ sinh số liên tục.

Đây là lợi ích về **tổ chức trạng thái và hiệu năng tiềm năng**, không phải lời hứa rằng mọi chương trình dùng `ThreadLocalRandom` đều nhanh hơn. Nếu tác vụ chủ yếu làm I/O, phải chờ khóa khác hoặc xử lý rất ít số, sự khác biệt có thể không đáng kể. Đo lường trên tải thực bằng benchmark phù hợp trước khi quyết định tối ưu.

`ThreadLocalRandom` không tự tạo nhiều luồng và cũng không thay thế `ExecutorService`: luồng/tác vụ vẫn phải được tổ chức bởi ứng dụng.

## 3. Những API cần biết và ý nghĩa khoảng

Các phương thức trả về giá trị **giả ngẫu nhiên**, không bảo đảm các giá trị khác nhau giữa các lần gọi.

| Lời gọi | Khoảng hoặc kết quả |
| --- | --- |
| `nextInt()` | Giá trị `int` trong toàn miền kiểu nguyên |
| `nextInt(10)` | Số nguyên từ `0` đến `9` |
| `nextInt(1, 7)` | Số nguyên từ `1` đến `6` |
| `nextLong(100L)` | Số `long` từ `0` đến `99` |
| `nextLong(10L, 20L)` | Số `long` từ `10` đến `19` |
| `nextDouble()` | `double` từ `0.0` (gồm) đến `1.0` (không gồm) |
| `nextDouble(1.5, 2.5)` | `double` trong khoảng `[1.5, 2.5)` |
| `nextBoolean()` | Giá trị `boolean` giả ngẫu nhiên |

Với các overload có `origin` và `bound`:

- Cận dưới **bao gồm** (`origin`).
- Cận trên **không bao gồm** (`bound`).
- Với `nextInt(origin, bound)` và `nextLong(origin, bound)`, phải có `origin < bound`; vi phạm sẽ ném `IllegalArgumentException`.
- Với `nextInt(bound)` hoặc `nextLong(bound)`, `bound` phải dương.
- Với `nextDouble(origin, bound)`, các cận phải hữu hạn và `origin < bound`; không dùng `NaN` hoặc vô cực làm cận.

Ví dụ muốn chọn chỉ số của một danh sách có `size` phần tử:

```java
int size = items.size();
if (size == 0) {
    throw new IllegalArgumentException("Danh sách không được rỗng");
}
int index = ThreadLocalRandom.current().nextInt(size);
// index thuộc [0, size): có thể dùng items.get(index).
```

Đây là **đoạn mã nhúng** với biến `items` là một `List` đã tồn tại. Nếu danh sách có thể bị sửa đồng thời, ứng dụng vẫn phải xử lý tính an toàn luồng của danh sách; `ThreadLocalRandom` không giải quyết việc đó.

### Tránh dùng phép chia dư để giới hạn ngẫu nhiên

```java
// Không nên dùng để sinh chỉ số [0, 9]:
int bad = ThreadLocalRandom.current().nextInt() % 10;

// Tránh cả Math.abs(nextInt()) % 10:
// Math.abs(Integer.MIN_VALUE) vẫn âm.
int good = ThreadLocalRandom.current().nextInt(10);
```

Giá trị của `bad` có thể âm. Phép `%` thủ công cũng có thể tạo độ lệch phân bố khi chia một miền kết quả không chia đều cho số lượng nhóm; dùng overload nhận `bound`/`origin, bound` để thư viện xử lý miền phù hợp.

`nextInt(1, 7)` thích hợp để mô phỏng một lần tung xúc xắc sáu mặt; đừng viết `nextInt(1, 6)` nếu muốn số `6` xuất hiện.

## 4. Quy tắc sở hữu trong ứng dụng đa luồng

### 4.1. Lấy `current()` bên trong worker

**Nên làm:**

```java
executor.submit(() -> {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    for (int i = 0; i < 1_000; i++) {
        int value = rng.nextInt(100);
        // Xử lý value bằng dữ liệu cục bộ của task.
    }
});
```

Đây là ví dụ *nhúng*; `executor` là một `ExecutorService` được tạo ở nơi gọi.

**Không nên giữ một instance và truyền qua các luồng:**

```java
// Sai ý định sử dụng API khi nhiều thread cùng gọi trên đối tượng này:
static final ThreadLocalRandom SHARED = ThreadLocalRandom.current();
```

`SHARED` được lấy trong luồng khởi tạo lớp; đem tham chiếu đó đi gọi từ các worker khác vi phạm hướng dẫn sở hữu theo luồng. Cũng tránh lấy `ThreadLocalRandom.current()` ở luồng chính rồi truyền biến `rng` vào mọi tác vụ qua lambda.

Đừng đồng bộ hóa thêm một `ThreadLocalRandom` dùng chung để “sửa” cách dùng sai. Cách đúng là **lấy giá trị từ `current()` trong chính luồng thực thi**.

### 4.2. Vòng đời theo worker thread, không theo task

`ExecutorService` thường tái sử dụng các luồng. Nếu 100 tác vụ chạy trên pool có 4 worker, nhiều tác vụ sẽ chạy nối tiếp trên cùng một worker, và mỗi tác vụ gọi `ThreadLocalRandom.current()` sẽ dùng bộ sinh của luồng đó. Vì vậy:

- Không khẳng định mỗi task có một seed độc lập hoặc một chuỗi ngẫu nhiên cố định.
- Không suy ra số bộ sinh từ số task.
- Lịch chạy task có thể thay đổi giữa các lần chạy.
- Không cần tự gọi `new ThreadLocalRandom()`; cách sử dụng thông thường là qua `current()`.

Nếu yêu cầu chính là **mỗi task có chuỗi giả ngẫu nhiên tái lập**, hãy thiết kế RNG và seed theo **định danh task** (xem mục 8); cơ chế thread-local không tương đương task-local.

### 4.3. RNG không làm dữ liệu chia sẻ trở nên an toàn

Ví dụ nhiều worker cùng cập nhật:

```java
long[] counts = new long[6];
executor.submit(() -> {
    int face = ThreadLocalRandom.current().nextInt(1, 7);
    counts[face - 1]++; // Có race nếu worker khác cũng cập nhật counts.
});
```

Phép tăng `counts[i]++` không phải thao tác nguyên tử. Nhiều luồng có thể mất lượt tăng dù chúng lấy số từ các bộ sinh độc lập. Có ba hướng xử lý thường gặp: mỗi task ghi vào bộ đếm **cục bộ rồi gộp khi hoàn tất**; hoặc dùng `AtomicLongArray` / `LongAdder` đúng vị trí nếu cần chia sẻ; hoặc đồng bộ hóa phù hợp. Ví dụ đầy đủ ở mục tiếp theo chọn cách đếm cục bộ.

Đừng nhầm **thread-safe RNG usage** với **thread-safe business data**.

## 5. Chương trình Java 17+ hoàn chỉnh: mô phỏng 40.000 lần tung xúc xắc

Tạo file **`ThreadLocalRandomDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

public class ThreadLocalRandomDemo {
    // Một worker sở hữu mảng counts, rồi chuyển kết quả về luồng tổng hợp.
    record Result(long[] counts, long outOfRange) {}

    public static void main(String[] args)
            throws InterruptedException, ExecutionException {

        int workers = 4;
        int drawsPerWorker = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            List<Future<Result>> futures = new ArrayList<>();

            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    // Phải lấy RNG ngay trong luồng chạy tác vụ.
                    ThreadLocalRandom rng = ThreadLocalRandom.current();
                    long[] counts = new long[6];
                    long outOfRange = 0;

                    for (int j = 0; j < drawsPerWorker; j++) {
                        int face = rng.nextInt(1, 7);
                        if (face >= 1 && face <= 6) {
                            counts[face - 1]++;
                        } else {
                            outOfRange++;
                        }
                    }
                    return new Result(counts, outOfRange);
                }));
            }

            // Main chỉ gộp sau khi nhận kết quả từng Future.
            long[] totals = new long[6];
            long invalid = 0;
            for (Future<Result> future : futures) {
                Result result = future.get();
                invalid += result.outOfRange();
                for (int i = 0; i < totals.length; i++) {
                    totals[i] += result.counts()[i];
                }
            }

            long counted = Arrays.stream(totals).sum() + invalid;
            System.out.println("workers=" + workers);
            System.out.println("draws=" + counted);
            System.out.println("outOfRange=" + invalid);
            System.out.println("buckets=" + totals.length);
            System.out.println("countMatches="
                    + (counted == (long) workers * drawsPerWorker));

        } finally {
            executor.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ThreadLocalRandomDemo.java
java ThreadLocalRandomDemo
```

**Stdout có thể kiểm chứng chính xác** (với giả định các tác vụ hoàn tất bình thường và API tuân thủ hợp đồng):

```text
workers=4
draws=40000
outOfRange=0
buckets=6
countMatches=true
```

Kết quả cụ thể của từng mặt xúc xắc sẽ thay đổi giữa các lần chạy nên ví dụ cố ý chỉ in các **bất biến**: số lượt sinh, tính hợp lệ của miền và số nhóm.

Cơ chế từng bước:

1. `newFixedThreadPool(4)` tạo executor với tối đa bốn worker để xử lý tác vụ. Bốn `Callable<Result>` được gửi vào executor.
2. Mỗi callable gọi `ThreadLocalRandom.current()` **khi đang chạy trong worker**, sau đó sinh 10.000 lần `nextInt(1, 7)`.
3. Mỗi tác vụ sử dụng mảng đếm riêng, không có phép tăng chia sẻ giữa các worker.
4. `Future.get()` chờ task hoàn tất hoặc báo lỗi; việc thu kết quả theo thứ tự submit **không buộc các tác vụ chạy theo thứ tự**.
5. Main gộp dữ liệu đã hoàn tất để tính tổng, rồi đóng executor bằng `shutdown()` trong `finally`.

`ExecutorService` ở Java 17 chưa nên được giả định là dùng được với `try-with-resources` trong tất cả môi trường biên dịch mục tiêu. Mẫu trên dùng `try/finally` rõ ràng; trong ứng dụng cần chính sách hủy/chờ kết thúc theo đặc thù hệ thống.

Nếu mỗi lần chạy in số lần xuất hiện của mặt 1..6 thì các con số sẽ không giống nhau hoàn toàn, và điều đó **không phải lỗi**. Một RNG tốt cũng không bảo đảm các nhóm bằng nhau chính xác trong một mẫu hữu hạn.

## 6. Hiệu năng: lợi ích và giới hạn thực tế

Trong chương trình nhiều luồng cùng sinh số với tần suất cao, dùng `ThreadLocalRandom` thường giảm tranh chấp so với việc chia sẻ một `Random` duy nhất. Tuy nhiên một phép đo đúng còn phụ thuộc vào:

- **Khối lượng công việc:** sinh vài số cho mỗi request thường có chi phí thấp hơn mạng, DB, log và các phép đồng bộ khác.
- **Cách ghi kết quả:** nếu mọi thread vẫn ghi vào một biến/cấu trúc có khóa chung thì contention có thể chỉ chuyển vị trí, không biến mất.
- **Số thread và kích thước pool:** thêm thread vượt khả năng xử lý CPU có thể làm tăng context switching; một pool hợp lý tùy tải, không mặc định càng lớn càng nhanh.
- **Warmup JVM/JIT, GC, phân phối lịch chạy:** benchmark một lần bằng `System.nanoTime()` khó chứng minh sự chênh lệch có ý nghĩa.
- **Yêu cầu kết quả:** nếu cần xác định đầu ra theo seed hoặc bảo mật, ưu tiên tiêu chuẩn đúng của bài toán trước việc giảm overhead.

Khi benchmark các bộ sinh, dùng JMH hoặc phương pháp đo nghiêm túc: chạy warmup, đo nhiều mẫu, dùng đủ thread, bảo đảm kết quả không bị compiler bỏ qua và xem cả throughput lẫn latency. Không tuyên bố một hệ số tăng tốc cố định cho mọi JDK/phần cứng.

### Điều gì xảy ra với `parallelStream()`?

Dùng RNG theo luồng xử lý *thực tế* của phần tử:

```java
long count = java.util.stream.IntStream.range(0, 10_000)
        .parallel()
        .filter(i -> ThreadLocalRandom.current().nextInt(10) == 0)
        .count();
```

Đoạn này có số lần “trúng” biến thiên; chỉ có giới hạn `0 <= count <= 10_000` là bất biến. Trên pool và khi phân hoạch workload thay đổi, kết quả cũng thay đổi. Không giữ một `ThreadLocalRandom.current()` lấy ở main rồi bắt tất cả nhánh của parallel stream sử dụng lại tham chiếu đó.

Không giả định một stream song song luôn mở riêng một thread cho mỗi phần tử. Việc song song hóa, độ lớn lô xử lý và thread pool do runtime quyết định.

## 7. Những ngoại lệ và sai lầm phổ biến

| Vấn đề | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `nextInt(0)` hoặc `nextInt(-1)` | `bound` không dương | Kiểm tra bound và trường hợp tập đầu vào rỗng |
| `nextInt(10, 10)` | `origin >= bound` | Dùng cận trên lớn hơn cận dưới |
| Chọn xúc xắc bằng `nextInt(1, 6)` | Bound trên là *exclusive* | Dùng `nextInt(1, 7)` |
| Dùng `Math.abs(nextInt()) % bound` | `MIN_VALUE` vẫn âm; `%` có thể gây lệch | Dùng `nextInt(bound)` |
| Lưu RNG vào biến `static final` để mọi worker dùng | Lấy instance trong một luồng rồi gọi ở luồng khác | Gọi `current()` ngay trong worker |
| Gọi `setSeed(...)` | `ThreadLocalRandom.setSeed(long)` luôn ném `UnsupportedOperationException` | Dùng RNG có seed công khai nếu cần tái lập |
| Tin `ThreadLocalRandom` bảo mật | Đây là PRNG không dùng cho mục đích mã hóa | Dùng `SecureRandom` cho token, khóa, mã xác minh |
| Dùng RNG riêng nhưng ghi `counts[i]++` lên mảng chung | Race condition trên dữ liệu dùng chung | Dùng bộ đếm cục bộ hoặc cấu trúc đồng bộ |
| Test chờ dãy số cố định / mọi bucket bằng nhau | Kết quả phụ thuộc seed, thread và ngẫu nhiên | Test miền giá trị, số lần, các bất biến, hoặc dùng RNG seeded |
| Mong một task có luồng/seed cố định trong thread pool | Executor tái sử dụng worker và có thể thay đổi lịch chạy | Ánh xạ RNG/seed theo task nếu cần tái lập |

Một lỗi nữa là dùng ngẫu nhiên để sinh **ID bắt buộc duy nhất**. PRNG có thể sinh cùng số nhiều lần. Nếu yêu cầu ID duy nhất, dùng chính sách đảm bảo duy nhất phù hợp (chẳng hạn cấp phát ID, UUID với hiểu biết về xác suất va chạm, hoặc ràng buộc duy nhất của kho dữ liệu); `ThreadLocalRandom` không bảo đảm uniqueness.

## 8. Khi cần tái lập kết quả hoặc bảo mật

### 8.1. Test xác định bằng seed

`ThreadLocalRandom` được khởi tạo với seed do runtime quản lý; API `setSeed(long)` không hỗ trợ. Vì vậy nó không phù hợp khi muốn cùng một phép thử luôn sinh lại đúng dãy số qua các lần chạy.

Trong Java 17 có thể dùng `SplittableRandom` để tạo các bộ sinh riêng cho mỗi tác vụ từ một seed gốc. Mỗi instance **không được chia sẻ giữa các luồng**; chia chúng trước khi gửi task:

```java
import java.util.SplittableRandom;

SplittableRandom root = new SplittableRandom(2026L);
SplittableRandom firstTaskRng = root.split();
SplittableRandom secondTaskRng = root.split();

// Giao firstTaskRng và secondTaskRng cho hai task riêng,
// mỗi instance chỉ được dùng trong một task.
```

Đây là mã minh họa cách chia bộ sinh, không phải toàn bộ chương trình chạy. Khi mỗi task nhận bộ sinh riêng, thực hiện cùng thứ tự lời gọi với cùng thuật toán/seed, kết quả của task có thể tái lập thuận tiện hơn, độc lập với việc task được xếp lên worker thread nào. Cần kiểm soát thứ tự cấp phát RNG, số lần rút và phiên bản/thuật toán nếu hợp đồng cần tính tái lập xuyên môi trường. Không dùng chung `SplittableRandom` giữa hai worker mà không thiết kế đồng bộ thích hợp.

`java.util.random.RandomGenerator` (Java 17) còn định nghĩa abstraction cho nhiều thuật toán RNG; chọn implementation theo yêu cầu hiệu năng, phân phối và khả năng tái lập, thay vì mặc định mọi bộ sinh giống nhau.

Đối với unit test có đầu ra cố định, lựa chọn khác là inject một `RandomGenerator` hoặc một interface tạo số vào thành phần đang kiểm thử, rồi dùng một fake/deterministic generator trong test.

### 8.2. Giá trị nhạy cảm về bảo mật

`ThreadLocalRandom` **không cryptographically secure**. Không dùng nó để sinh mật khẩu tạm, mã OTP, session token, API secret, nonce phải không dự đoán được hoặc khóa bảo mật. Những trường hợp này dùng `java.security.SecureRandom` và thiết kế quy trình an toàn phù hợp. Việc nhiều luồng cần tạo token không biến `ThreadLocalRandom` thành lựa chọn bảo mật; kiến trúc chia sẻ/tạo instance `SecureRandom` phải được đánh giá riêng theo yêu cầu thông lượng và provider.

## 9. Thiết kế kiểm thử cho tính đúng đắn trong đa luồng

**Thử nghiệm bảo đảm (deterministic assertions):**

- Với `rng.nextInt(1, 7)`, kiểm tra từng kết quả nằm từ 1 đến 6.
- Với `workers × drawsPerWorker`, tổng số mẫu đếm được phải bằng số lượt yêu cầu (nếu không có lỗi task).
- Số mẫu không hợp lệ phải bằng 0.
- Kết quả từ các worker chỉ được gộp sau khi hoàn tất, hoặc dùng cơ chế đồng bộ đúng.
- Nếu task ném ngoại lệ, `Future.get()` cho biết thất bại thay vì báo thành công.

**Không nên kiểm thử:**

- “Mặt số 6 nhất định sẽ xuất hiện trong 100 lần.”
- “Tổng các mặt chẵn bằng tổng các mặt lẻ.”
- “Hai lần chạy phải cho cùng dãy số.”
- “Mỗi thread luôn sinh giá trị khác thread khác.”

Các khẳng định này không được API bảo đảm. Nếu cần kiểm tra thống kê phân bố, dùng số lượng mẫu và phương pháp phân tích thống kê phù hợp, chấp nhận xác suất thất bại được lượng hóa. Kiểm tra thống kê không thay thế kiểm tra cận và race condition.

Có thể dùng công cụ kiểm tra concurrency để đánh giá cập nhật trạng thái chia sẻ, nhưng việc `nextInt` tạo kết quả trong miền hợp lệ chỉ chứng minh RNG; không chứng minh phần còn lại của workflow thread-safe.

## 10. Bài tập vận dụng

1. Mười task dùng thread pool bốn worker. Có nên lưu một `ThreadLocalRandom` ở main và truyền vào tất cả task không? Vì sao gọi `ThreadLocalRandom.current()` trong từng task đúng hơn?
2. Viết một hàm chọn ngẫu nhiên chỉ số của `List<String>`, từ chối danh sách rỗng. Xác định cận dưới và cận trên chính xác.
3. Thay chương trình xúc xắc bằng mô phỏng tung đồng xu 100.000 lần trên nhiều worker; giữ bộ đếm cục bộ và chỉ in `total == 100000`, `invalid == 0`.
4. Nếu product manager yêu cầu test mô phỏng chạy lặp lại đúng kết quả trên cùng seed, có phù hợp dùng `ThreadLocalRandom` không? Đề xuất thiết kế dùng RNG theo task và seed rõ ràng.
5. Một hệ thống sinh mã xác thực đăng nhập đang dùng `nextInt(100000, 1000000)`. Vì sao miền giá trị đúng vẫn **chưa đủ** để phương pháp trở nên an toàn?

**Đáp án gợi ý:** (1) Không truyền instance lấy từ luồng khác; lấy `current()` tại worker. (2) `nextInt(list.size())` cho `[0, size)` sau khi kiểm tra `size > 0`. (3) Mỗi task trả bộ đếm riêng, main gộp bằng `Future.get()`. (4) Dùng `SplittableRandom`/bộ sinh có seed theo task và cố định thứ tự sử dụng. (5) `ThreadLocalRandom` không có tính chất bảo mật chống dự đoán, dù đầu ra là số sáu chữ số.

## 11. Tóm tắt quyết định kỹ thuật

Dùng `ThreadLocalRandom.current().nextX(...)` **ngay trong luồng đang làm việc** khi nhiều worker phải sinh số giả ngẫu nhiên thường xuyên và không cần dãy kết quả tái lập theo seed. Dùng đúng cận trên loại trừ và giữ dữ liệu tích lũy cục bộ hoặc đồng bộ đúng cách. Nếu phải tái lập dãy số theo task, chọn bộ sinh có seed và lifecycle rõ ràng; nếu đầu ra cần chống dự đoán, chuyển sang `SecureRandom`.

Tài liệu tham chiếu (Java SE 17): [ThreadLocalRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadLocalRandom.html), [SplittableRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SplittableRandom.html), [SecureRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-005">SplittableRandom và chia nguồn ngẫu nhiên</a>

<details>
<summary>Xem chi tiết</summary>

# SplittableRandom và chia nguồn ngẫu nhiên trong Java

## 1. Vấn đề: vì sao cần “chia” nguồn sinh số ngẫu nhiên?

Khi chương trình chỉ chạy một chuỗi thao tác, ta có thể tạo một bộ sinh số giả ngẫu nhiên (*pseudorandom number generator*, PRNG) rồi gọi lần lượt `nextInt()`, `nextLong()` hoặc `nextDouble()`. Trong ứng dụng thực tế, công việc có thể tách thành nhiều nhánh: mô phỏng nhiều kịch bản, xử lý một tập dữ liệu theo các tác vụ độc lập hoặc dùng `ExecutorService` / fork-join để chạy song song.

Nếu nhiều tác vụ cùng dùng **một đối tượng sinh số có trạng thái**, chúng có thể tranh chấp trạng thái, gây khó tái lập kết quả hoặc vi phạm hợp đồng về an toàn luồng. `java.util.SplittableRandom` giải quyết bài toán này bằng thao tác **`split()`**: tạo một đối tượng sinh số mới cho nhánh con. Mỗi nhánh sở hữu bộ sinh riêng và tự lấy các số tiếp theo mà không phải truy cập cùng một generator.

`SplittableRandom` xuất hiện từ Java 8, có trong Java 17+, phù hợp với việc tạo dữ liệu ngẫu nhiên hiệu năng tốt và các phép tính chia nhánh. Đây là **bộ sinh giả ngẫu nhiên**, không phải nguồn ngẫu nhiên mật mã; kết quả phụ thuộc trạng thái và thuật toán, không bảo đảm bí mật hay khó dự đoán trước khi kẻ tấn công biết seed/trạng thái.

Mục tiêu sau bài học: hiểu `split()` làm gì; cách tổ chức cây generator cha-con; cách cung cấp seed để tái lập; cách cấp một generator cho từng tác vụ; cách dùng phạm vi giá trị của API; và những trường hợp phải chọn giải pháp khác.

## 2. Tạo SplittableRandom và gọi các phương thức cơ bản

Hai cách tạo thường gặp:

```java
import java.util.SplittableRandom;

// Seed tường minh: phù hợp cho thử nghiệm có thể tái lập.
SplittableRandom reproducible = new SplittableRandom(2026L);

// Không truyền seed: hệ thống tạo seed ban đầu.
SplittableRandom ordinary = new SplittableRandom();
```

Những API thường dùng:

| Phương thức | Ý nghĩa | Phạm vi |
|---|---|---|
| `nextInt()` | Số nguyên 32-bit giả ngẫu nhiên | Toàn bộ miền của `int` |
| `nextLong()` | Số nguyên 64-bit giả ngẫu nhiên | Toàn bộ miền của `long` |
| `nextDouble()` | Số thực từ 0 tới dưới 1 | `[0.0, 1.0)` |
| `nextInt(10)` | Số nguyên từ 0 tới dưới 10 | `0..9` |
| `nextInt(5, 15)` | Số nguyên từ 5 tới dưới 15 | `5..14` |
| `nextLong(10L, 20L)` | Số `long` từ 10 tới dưới 20 | `10..19` |
| `ints(5, 0, 10)` | `IntStream` có 5 phần tử trong khoảng 0 đến dưới 10 | Mỗi phần tử `0..9` |
| `split()` | Sinh một `SplittableRandom` con để cấp cho nhánh công việc | Generator mới |

Các khoảng có cận trên **loại trừ** (*exclusive*). Vì thế, `nextInt(10)` không trả 10. Với các overload có `origin` và `bound`, phải có `origin < bound`; `nextInt(0)` hoặc `nextInt(10, 10)` là yêu cầu không hợp lệ và ném `IllegalArgumentException`.

Phương thức `ints(...)` tạo luồng giá trị `int` để xử lý bằng Stream API. Đây là **luồng số lấy từ một generator**, khác với `splits(...)` (Java 17+) tạo luồng **các generator con**. Khi một stream số được tiêu thụ, generator liên quan tiến trạng thái theo các lần sinh giá trị.

## 3. split() hoạt động như thế nào?

`SplittableRandom` là đối tượng **có trạng thái** (*stateful*). Mỗi lần gọi `nextInt()` hoặc các phương thức sinh số, trạng thái tiến lên. `split()` cũng sử dụng và cập nhật trạng thái của generator cha để tạo **generator con mới với trạng thái riêng**. Sau `split()`:

1. Generator cha **vẫn sử dụng được** để sinh số hoặc tạo các nhánh khác.
2. Generator con là **đối tượng riêng**, có thể tiến trạng thái độc lập với cha và các con khác.
3. Không nên coi con là bản sao có cùng chuỗi số với cha; mục tiêu của thao tác chia là tạo các dòng giả ngẫu nhiên **được thiết kế để ít tương quan**, không phải nhân bản y hệt.
4. Lời gọi `split()` có ảnh hưởng đến trạng thái cha. Vì vậy **thứ tự chia**, số lần chia và thứ tự lấy số là một phần của diễn tiến generator.

Minh họa cách cấp generator cho cây tác vụ:

```text
root = new SplittableRandom(seed)
   |
   +-- root.split() --> generator A --> các lần nextInt riêng của task A
   |
   +-- root.split() --> generator B --> các lần nextInt riêng của task B
   |
   +-- root.split() --> generator C --> các lần nextInt riêng của task C
   |
   +-- root vẫn có thể split tiếp hoặc tự sinh số
```

Điểm cần nhớ: **chia generator trước khi giao công việc cho từng tác vụ**. Sau khi giao, mỗi tác vụ chỉ dùng generator của mình. Không nên đồng thời gọi `root.split()` trên cùng đối tượng `root` từ nhiều luồng.

Không cần và không nên trông chờ mỗi generator con đều sinh ra các số **không bao giờ trùng nhau**. Hai nhánh độc lập vẫn có thể tình cờ sinh cùng một số, chẳng hạn cùng nhận `7` từ `nextInt(10)`. “Chia nguồn” nói về việc tổ chức trạng thái và chất lượng các dòng ngẫu nhiên, **không phải cam kết mọi giá trị xuất ra đều duy nhất**.

### Vì sao không tự tạo các generator bằng cùng seed?

Đoạn sau là một lỗi thường gặp:

```java
SplittableRandom a = new SplittableRandom(2026L);
SplittableRandom b = new SplittableRandom(2026L);
```

Nếu hai đối tượng cùng seed và dùng cùng chuỗi lời gọi, chúng cho **cùng chuỗi giá trị**. Đây là điều có ích cho kiểm thử tái lập, nhưng không phải cách cấp hai luồng giả ngẫu nhiên khác nhau cho hai tác vụ. Nếu đã có một generator gốc, dùng `root.split()` để lấy từng nguồn con.

Cũng không cần mô phỏng `split()` bằng `new SplittableRandom(root.nextLong())`. API `split()` đã được thiết kế riêng cho việc tạo generator con và quản lý trạng thái chia nguồn; hãy dùng đúng API.

## 4. Ví dụ Java 17+: hai nhánh và hai tác vụ song song

Tạo file **`RandomSplitDemo.java`** với chương trình chạy độc lập dưới đây:

```java
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RandomSplitDemo {
    private static int[] draw(SplittableRandom rng) {
        // Đúng 5 số, mỗi số thuộc [0, 10).
        return rng.ints(5, 0, 10).toArray();
    }

    private static int[][] makePair(long seed) {
        SplittableRandom root = new SplittableRandom(seed);

        // Thứ tự chia nguồn cố định.
        SplittableRandom childA = root.split();
        SplittableRandom childB = root.split();

        return new int[][] {draw(childA), draw(childB)};
    }

    private static int[] parallelSums(long seed) throws Exception {
        SplittableRandom root = new SplittableRandom(seed);

        // Tạo cả hai nguồn ở luồng điều phối trước khi submit.
        SplittableRandom rngA = root.split();
        SplittableRandom rngB = root.split();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(
                    () -> rngA.ints(1000, 0, 10).sum());
            Future<Integer> b = pool.submit(
                    () -> rngB.ints(1000, 0, 10).sum());

            // Đọc theo thứ tự nhiệm vụ A rồi B.
            return new int[] {a.get(), b.get()};
        } finally {
            pool.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        int[][] first = makePair(2026L);
        int[][] again = makePair(2026L);

        System.out.println("repeatable: "
                + Arrays.deepEquals(first, again));

        System.out.println("two children, five numbers each: "
                + (first.length == 2
                && first[0].length == 5
                && first[1].length == 5));

        System.out.println("range [0, 10): "
                + Arrays.stream(first)
                        .flatMapToInt(Arrays::stream)
                        .allMatch(n -> n >= 0 && n < 10));

        int[] p1 = parallelSums(2026L);
        int[] p2 = parallelSums(2026L);

        System.out.println("parallel repeatable: "
                + Arrays.equals(p1, p2));
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac RandomSplitDemo.java
java RandomSplitDemo
```

Kết quả:

```text
repeatable: true
two children, five numbers each: true
range [0, 10): true
parallel repeatable: true
```

Giải thích từng phần:

- `makePair(2026L)` tạo generator cha với seed cố định, sau đó **chia A trước B**, rồi mỗi con sinh 5 số. Gọi lại với cùng seed và cùng trình tự tạo/chạy cho cùng hai mảng; kiểm tra dùng `Arrays.deepEquals` vì dữ liệu có kiểu `int[][]`.
- `ints(5, 0, 10)` lấy đúng 5 phần tử trong khoảng `[0,10)`. Kiểm tra `allMatch` xác nhận quy tắc về cận; đây không phải phép kiểm định chất lượng thống kê.
- `parallelSums` cấp `rngA` và `rngB` riêng biệt **trước khi** gọi `submit`. Mỗi tác vụ tính tổng 1.000 giá trị nguyên bằng generator của mình. Không có hai tác vụ cùng truy cập một `SplittableRandom`.
- Gọi `Future.get()` chờ kết quả hoàn tất. Kết quả hai lần giống nhau vì cùng seed, cùng thứ tự split và mỗi nhánh tiêu thụ số lần lấy giá trị cố định. Thứ tự hai công việc được hệ điều hành xếp chạy không ảnh hưởng đến trạng thái generator của nhánh kia.
- `pool.shutdown()` ở `finally` đóng executor sau khi dùng. Đây là phần quản lý tài nguyên, không liên quan đến thuật toán tạo số ngẫu nhiên.

Chương trình chủ ý in **tính chất có thể kiểm chứng** thay vì chép mười số giả ngẫu nhiên cụ thể. Nếu cần xem giá trị thực, thêm `System.out.println(Arrays.deepToString(first));`. Các số có thể trùng giữa hai nhánh mà điều đó không làm sai cơ chế `split()`.

## 5. Tính tái lập: seed là cần thiết nhưng chưa đủ

Với `new SplittableRandom(seed)`, một luồng gọi hàm **cố định** sẽ tái lập chuỗi sinh ra. Khi tạo cây generator, kết quả còn phụ thuộc cách chia và cách dùng từng nhánh.

Để một phép thử tái lập ổn định, cố định tất cả yếu tố:

1. **Seed gốc**: cùng seed khi khởi tạo generator cha.
2. **Cấu trúc phân nhánh**: cùng số lượng `split()`, cùng thứ tự tạo A, B, C.
3. **Phân công nguồn**: cùng nhánh công việc nhận đúng generator con của nó.
4. **Trình tự và số lượng lời gọi trong từng nhánh**: ví dụ mỗi nhánh gọi `nextInt(100)` chính xác 1.000 lần.
5. **Dữ liệu và thuật toán xử lý**: nếu thay đổi điều kiện hoặc thứ tự gọi ngẫu nhiên, kết quả đầu ra cũng có thể đổi.

Ví dụ này **không bảo đảm tái lập** dù seed giống nhau:

```java
SplittableRandom shared = new SplittableRandom(2026L);

// Không dùng cùng một shared trong hai tác vụ chạy song song.
// Việc luồng nào gọi nextInt() trước còn tùy thời điểm chạy.
```

Ngoài vấn đề tái lập, cách chia sẻ trên còn sai về an toàn luồng. Một lỗi tinh vi khác là để từng tác vụ gọi `root.split()` đồng thời: như vậy generator cha trở thành trạng thái chia sẻ. Cách đơn giản và đúng là tạo nguồn con **ở luồng điều phối** rồi giao từng nguồn cho một tác vụ.

Nếu chia việc động (ví dụ fork-join sinh thêm nhiệm vụ tùy dữ liệu), nhánh tạo nhiệm vụ có thể gọi `split()` trên **generator do chính nhánh đó sở hữu** rồi giao con cho nhiệm vụ mới. Điều kiện là không có hai nhiệm vụ cùng thao tác trên một generator tại cùng thời điểm. Muốn tái lập xuyên các lần chạy, cấu trúc chia và lượng dữ liệu tiêu thụ cũng cần ổn định, hoặc cần một chiến lược seed gắn với định danh tác vụ đã được thiết kế rõ ràng.

### Cùng seed không có nghĩa kết quả luôn giống qua mọi thay đổi mã

Nếu thêm một lời gọi `nextInt()` trước `split()`, đổi thứ tự `split()`, hoặc chuyển nhánh nào tiêu thụ bao nhiêu số, các chuỗi tiếp theo có thể đổi. Vì vậy đừng xem seed đơn lẻ như “ảnh chụp” toàn bộ trạng thái một phép tính song song.

Một bộ kiểm thử có thể ghi lại seed, cấu trúc phân nhánh và phiên bản thuật toán nếu việc phát lại chuỗi là yêu cầu quan trọng. Với ví dụ trong một lần chạy trên cùng JDK, việc cố định seed và trình tự thao tác có thể kiểm tra được như chương trình ở trên.

## 6. An toàn luồng và hiệu năng: mỗi tác vụ một generator

Tài liệu API của Java nêu rõ các đối tượng `SplittableRandom` **không thread-safe**: không được coi một instance là tài nguyên dùng đồng thời an toàn giữa nhiều luồng. Chúng được thiết kế để **chia ra và sở hữu riêng**, phù hợp với các phép tính phân nhánh, nhất là fork-join.

Quy tắc tổ chức:

| Cách dùng | Đánh giá | Lý do |
|---|---|---|
| Một luồng duy nhất dùng một `SplittableRandom` | Phù hợp | Không có truy cập đồng thời |
| Cha `split()` tuần tự rồi mỗi task nhận một con | Phù hợp | Trạng thái từng generator độc lập theo chủ sở hữu |
| Hai task cùng gọi `nextInt()` trên cùng instance | Tránh | `SplittableRandom` không thread-safe |
| Nhiều task cùng gọi `split()` trên cùng cha | Tránh | Cùng sửa trạng thái cha, có thể tranh chấp |
| Mỗi task tạo `new SplittableRandom(2026L)` | Thường không phù hợp | Các task có thể sinh cùng chuỗi nếu thao tác giống nhau |
| Mỗi task giữ generator của mình và con tạo việc nhỏ hơn bằng `split()` | Phù hợp khi quản lý sở hữu chặt | Tạo cấu trúc chia đệ quy, không chia sẻ đối tượng |

**Hiệu năng:** ý tưởng chia nguồn giúp giảm nhu cầu đồng bộ hóa quanh một generator chung trong các công việc song song. Tuy nhiên, nó **không tự làm mọi thuật toán nhanh hơn**: thời gian tạo tác vụ, gom kết quả và số lượng phép tính vẫn quyết định hiệu năng thực tế. Khi công việc rất nhỏ, overhead của song song có thể lớn hơn lợi ích. Không cần tự gọi `split()` cho mọi số ngẫu nhiên đơn lẻ; hãy tạo một generator cho mỗi **đơn vị công việc độc lập** và dùng nhiều lần.

## 7. So sánh với Random, ThreadLocalRandom và SecureRandom

| API | Cách quản lý | Khi nào phù hợp |
|---|---|---|
| `java.util.Random` | PRNG dùng tuần tự; có thể chia sẻ nhưng đồng thời có thể gây tranh chấp | Mã truyền thống, tác vụ đơn giản, yêu cầu API cũ |
| `ThreadLocalRandom.current()` | Generator riêng gắn với luồng thực thi, do thư viện quản lý | Khi muốn tiện lợi trong mã đa luồng và không cần tự kiểm soát seed cho từng tác vụ |
| `SplittableRandom` | PRNG có thể chia thành nhiều generator con bằng `split()` | Thuật toán phân nhánh, fork-join, mô phỏng nhiều tác vụ, tái lập qua seed + cấu trúc chia ổn định |
| `java.security.SecureRandom` | Bộ sinh dành cho yêu cầu bảo mật | Token, nonce, khóa và các giá trị cần tính khó dự đoán phù hợp yêu cầu bảo mật |

`ThreadLocalRandom.current()` hữu ích khi mỗi luồng đơn giản muốn lấy số mà không chia sẻ một `Random` chung, nhưng generator gắn với luồng không đồng nghĩa với generator gắn với **tác vụ logic**: tác vụ có thể chạy trên các luồng khác nhau. `SplittableRandom` cho phép giao **một instance cụ thể cho đúng tác vụ**, dễ kiểm soát ở mô hình chia nhánh.

Không dùng `SplittableRandom` để sinh mật khẩu, session token, mã khôi phục, khóa hoặc dữ liệu bí mật. Biết seed hoặc đoán được trạng thái có thể khiến chuỗi số giả ngẫu nhiên dự đoán được; trong các trường hợp bảo mật, dùng API `SecureRandom` phù hợp.

### Quan hệ với RandomGenerator trong Java 17

Java 17 cung cấp họ interface `java.util.random.RandomGenerator`. `SplittableRandom` thực hiện `RandomGenerator.SplittableGenerator`, hỗ trợ các khả năng chia nguồn trong API thống nhất. `SplittableRandom.split()` trả về `SplittableRandom`, nên có thể trực tiếp gán con vào biến cùng kiểu. Trong Java 17+ cũng có các phương thức thuộc mô hình `splits()` / `splits(long)` để tạo stream các generator con.

Ví dụ tạo ba generator con:

```java
import java.util.List;
import java.util.SplittableRandom;

SplittableRandom root = new SplittableRandom(2026L);
List<SplittableRandom> children = root.splits(3).toList();

// 3 nguồn riêng; sau đó giao mỗi nguồn cho một tác vụ.
```

Đoạn trên là **mã minh họa đặt trong một phương thức** (không phải chương trình độc lập). `toList()` của Stream có từ Java 16. `splits(3)` sinh stream gồm ba generator; việc tiêu thụ stream sẽ thực hiện các lần chia, nên phải có chiến lược sở hữu rõ ràng, nhất là nếu chọn pipeline song song. Đối với số tác vụ cố định và cần dễ kiểm tra thứ tự, vòng lặp `root.split()` tuần tự thường dễ hiểu hơn.

## 8. Lỗi thường gặp và cách kiểm tra

### Nhầm việc chia nguồn với chia dải số

`root.split()` **không** có nghĩa A chỉ sinh số từ 0–4, còn B chỉ sinh 5–9. Cả A và B vẫn có thể gọi `nextInt(0, 10)` và sinh ra bất kỳ số nào trong `[0,10)`. Chia nguồn phân tách **trạng thái PRNG**, không phân vùng kết quả đầu ra.

Nếu bài toán yêu cầu ID không trùng hoặc phân bổ chính xác từng giá trị, hãy dùng chiến lược cấp ID, phân đoạn tập giá trị hoặc khóa/transaction thích hợp. Nguồn số ngẫu nhiên độc lập không thay thế các đảm bảo tính duy nhất.

### Cho rằng split “nhân đôi” trạng thái cha

Nếu tạo `child = root.split()` rồi gọi `child.nextInt()` và `root.nextInt()`, không có yêu cầu hai kết quả giống nhau. `split()` sinh một generator mới; đây không phải `clone()`, không phải copy object và không phải phép “đánh dấu” mà không đổi trạng thái cha.

### Dùng cùng generator ở nhiều luồng và thấy đôi lúc vẫn chạy

Việc chương trình ít tải chưa xuất hiện lỗi không chứng minh dùng chung generator là an toàn. Truy cập đồng thời vào generator không thread-safe có thể gây dữ liệu không đúng kỳ vọng và phá tính tái lập. Luôn thiết lập chủ sở hữu của mỗi generator theo nhiệm vụ.

### Đổi thứ tự tạo nhánh nhưng vẫn kỳ vọng kết quả giống

Nếu lần một A dùng con của `split()` thứ nhất còn lần hai A nhận con thứ hai, chuỗi của A có thể đổi. Khi xây test song song cần cố định cách ánh xạ **tác vụ → generator con**. Chỉ fixed seed là không đủ khi lịch chia hoặc tiêu thụ nguồn thay đổi.

### Nhầm ngẫu nhiên giả với bảo mật hoặc “ngẫu nhiên thật”

`SplittableRandom` có chất lượng PRNG hữu ích cho nhiều phép mô phỏng và chia nhánh, nhưng không cung cấp tính chất mật mã. Và ngay cả với các nhánh riêng, không có lời hứa rằng mỗi số sinh ra đều khác tất cả số trước đó. Kiểm tra dữ liệu theo xác suất, phân phối, miền giá trị và yêu cầu ứng dụng; đừng rút kết luận thống kê từ một vài mẫu.

### Tạo quá nhiều generator hoặc tác vụ

Có thể chia cây generator sâu theo cây công việc, nhưng mỗi nguồn và mỗi tác vụ đều có chi phí. Nếu chỉ cần lấy một chuỗi 10.000 số ở một luồng, một generator duy nhất thường đơn giản hơn. Chỉ chia nguồn khi thật sự có **công việc độc lập**, nhất là khi thực thi song song.

## 9. Cách áp dụng và tự kiểm tra

Để dùng `SplittableRandom` cho một tác vụ gồm N nhánh:

1. Tạo `root`. Dùng seed cố định cho kiểm thử; cân nhắc nguồn seed phù hợp khi chạy thực tế.
2. Xác định các nhánh có thể tính độc lập và nhánh nào cần bộ sinh riêng.
3. Ở luồng điều phối, gọi `root.split()` đúng một lần cho mỗi nhánh và **gắn con với định danh nhánh**.
4. Giao mỗi con cho một tác vụ; tác vụ chỉ thao tác trên generator của mình.
5. Khi tác vụ tự chia nhỏ, chỉ generator của tác vụ đó mới được dùng để `split()` thêm.
6. Nếu cần tái lập, giữ cố định seed, cây chia, thứ tự ánh xạ và lượng lời gọi; viết test so sánh kết quả của hai lần chạy.
7. Không dùng `SplittableRandom` cho mục đích mật mã.

**Tự kiểm tra 1:** Hai tác vụ đều dùng `new SplittableRandom(7L)` và cùng gọi `nextInt(100)` ba lần. Chúng có tạo hai dòng khác nhau không? **Không:** cùng seed và chuỗi lời gọi dẫn đến cùng chuỗi kết quả. Nên chia từ một generator cha nếu muốn hai nguồn khác nhau.

**Tự kiểm tra 2:** Gọi `SplittableRandom child = root.split()`, rồi task A dùng `root` còn task B dùng `child` có hợp lệ không? **Có**, nếu không luồng nào khác dùng cùng các đối tượng đó trong khi chạy. Cha vẫn sử dụng được; tuy nhiên để dễ quản lý, thường cấp riêng từng con và giữ cha làm bộ điều phối.

**Tự kiểm tra 3:** Task A và task B cùng trả `5` từ `nextInt(10)`. Điều này có chứng minh `split()` bị lỗi không? **Không**. Hai dòng giả ngẫu nhiên có thể xuất ra cùng một giá trị. Điều quan trọng là trạng thái và truy cập được quản lý riêng.

**Ghi nhớ:** `SplittableRandom` là PRNG **có thể chia**. Gọi `split()` ở nhánh đang sở hữu generator rồi **giao con cho tác vụ độc lập**. Cùng seed và cùng cấu trúc chia/cách tiêu thụ giúp kiểm thử tái lập; chia nguồn không bảo đảm giá trị duy nhất, không tự bảo đảm tăng tốc và không cung cấp bảo mật mật mã.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-006">RandomGenerator và RandomGeneratorFactory từ Java 17</a>

<details>
<summary>Xem chi tiết</summary>

# RandomGenerator và RandomGeneratorFactory từ Java 17

## 1. Vì sao Java 17 bổ sung API số ngẫu nhiên?

Trước Java 17, nhiều chương trình quen với `java.util.Random`, `ThreadLocalRandom`, `SplittableRandom` hoặc `SecureRandom`. Mỗi lựa chọn có thế mạnh riêng, nhưng việc thay đổi thuật toán và truy vấn đặc điểm của bộ sinh còn thiếu một API thống nhất. **Java 17 giới thiệu `java.util.random.RandomGenerator` và `RandomGeneratorFactory`** như giao diện và cơ chế lựa chọn thuật toán trong `java.base` (JEP 356).

Bài này tập trung vào hai thành phần:

- **`RandomGenerator`**: giao diện chung để **sử dụng** một bộ sinh số ngẫu nhiên, thường là số *giả ngẫu nhiên* (*pseudorandom*).
- **`RandomGeneratorFactory<T extends RandomGenerator>`**: factory để **tìm, lựa chọn, kiểm tra đặc tính và tạo** các instance của một thuật toán cụ thể.

Hai vai trò bổ sung cho nhau: mã nghiệp vụ nên phụ thuộc vào `RandomGenerator` khi chỉ cần phát sinh số; mã cấu hình, khởi tạo hoặc lựa chọn thuật toán mới cần quan tâm đến `RandomGeneratorFactory`.

```java
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

RandomGeneratorFactory<RandomGenerator> factory =
        RandomGeneratorFactory.of("L64X128MixRandom");
RandomGenerator rng = factory.create();

int die = rng.nextInt(1, 7); // 1, 2, 3, 4, 5 hoặc 6
```

Đây là **đoạn mã minh họa** để đặt trong một method hoặc lớp Java, chưa phải chương trình độc lập. Ví dụ đầy đủ với lệnh `javac`/`java` ở phần 7.

### Giả ngẫu nhiên có nghĩa là gì?

Một thuật toán giả ngẫu nhiên tạo dãy kết quả từ **trạng thái nội bộ**. Mỗi lần gọi như `nextInt()` thường làm bộ sinh tiến sang trạng thái tiếp theo. Dãy số được thiết kế có đặc tính thống kê hữu ích, nhưng về nguyên tắc xác định bởi thuật toán và trạng thái/seed ban đầu.

Vì vậy, “random” ở đây **không đồng nghĩa với ngẫu nhiên dùng cho bảo mật**. Seed dễ đoán hoặc thuật toán không có khả năng chống dự đoán sẽ không thích hợp cho token đăng nhập, khóa, mã OTP hoặc bí mật phiên. Với yêu cầu bảo mật, dùng `java.security.SecureRandom` và thiết kế bảo mật chuyên biệt; `SecureRandom` cũng thực thi `RandomGenerator` trong Java 17 nhưng không thể thay bằng bất kỳ PRNG nhanh nào.

## 2. Phân biệt giao diện, factory và thuật toán

`RandomGenerator` là một **interface**, không thể dùng `new RandomGenerator()`. Nó cung cấp các phương thức như `nextInt()`, `nextLong()`, `nextDouble()` và stream để ứng dụng tiêu thụ kết quả.

`RandomGeneratorFactory` là một **lớp factory có tham số kiểu**, mỗi factory gắn với một thuật toán nhất định. Ví dụ `RandomGeneratorFactory.of("L64X128MixRandom")` tìm factory cho thuật toán có tên đã chọn; `factory.create()` tạo một instance để dùng.

```text
Tên thuật toán ──→ RandomGeneratorFactory ──create()──→ RandomGenerator
                    │                         │
                    │ name(), stateBits()     └── nextInt(), nextDouble(), ...
                    │ period(), isSplittable()
                    └── thông tin và khả năng của thuật toán
```

Các lựa chọn tạo bộ sinh:

| Cách tạo | Tác dụng | Khi nào dùng |
| --- | --- | --- |
| `RandomGenerator.getDefault()` | Tạo một bộ sinh với thuật toán mặc định của runtime. | Không yêu cầu cụ thể về danh tính thuật toán. |
| `RandomGenerator.of("L64X128MixRandom")` | Tạo bộ sinh theo tên thuật toán. | Muốn thuật toán có tên xác định, không cần quản lý factory. |
| `RandomGeneratorFactory.getDefault().create()` | Lấy factory mặc định rồi tạo bộ sinh. | Cần thêm metadata từ factory hoặc muốn tạo nhiều instance. |
| `RandomGeneratorFactory.of(name).create()` | Tạo bộ sinh từ thuật toán được chọn. | Khởi tạo có cấu hình tên thuật toán. |
| `RandomGeneratorFactory.of(name).create(seed)` | Yêu cầu tạo bộ sinh từ seed `long`. | Thí nghiệm, tái lập kết quả và kiểm thử theo thuật toán cố định. |
| `RandomGeneratorFactory.all()` | Stream các factory khả dụng, không gồm thuật toán bị đánh dấu deprecated. | Khám phá/lọc thuật toán theo khả năng, metadata. |

**Đừng giả định thuật toán mặc định bất biến theo JDK hoặc phiên bản.** Đặc tả `getDefault()` cho phép lựa chọn thuật toán thay đổi theo thời gian. Nếu cần tái hiện dãy từ một thuật toán cụ thể, hãy chọn **tên thuật toán**, kiểm soát seed và thứ tự các lời gọi.

Với `RandomGeneratorFactory.of(name)`, tên không tồn tại sẽ gây `IllegalArgumentException`; không nên tự nhận mọi thuật toán có trong một runtime khác đều hiện diện ở runtime của mình.

## 3. Cách lấy và sử dụng bộ sinh trong Java 17

### 3.1. Lựa chọn mặc định

```java
RandomGenerator rng = RandomGenerator.getDefault();

int anyInt = rng.nextInt();
long anyLong = rng.nextLong();
double unit = rng.nextDouble(); // [0.0, 1.0)
boolean flag = rng.nextBoolean();
```

Các lời gọi trên cung cấp các kiểu cơ bản. `nextInt()` không truyền phạm vi có thể tạo bất kỳ giá trị `int` nào; `nextDouble()` tạo một giá trị không âm, nhỏ hơn 1.0. Giá trị thực tế **thay đổi theo trạng thái bộ sinh**. Không nên ghi một dãy stdout cụ thể cho bộ sinh không có seed được kiểm soát.

### 3.2. Chọn thuật toán theo tên

```java
RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");
System.out.println(rng.nextInt(100)); // một số nguyên từ 0 đến 99
```

Tên `L64X128MixRandom` là một thuật toán thuộc nhóm **LXM** có trong Java 17. Tên thuật toán là cấu hình kỹ thuật, không nên nhầm với tên lớp triển khai có thể thay đổi hay định danh của một instance.

### 3.3. Factory và seed

```java
RandomGeneratorFactory<RandomGenerator> factory =
        RandomGeneratorFactory.of("L64X128MixRandom");

RandomGenerator first = factory.create(12345L);
RandomGenerator second = factory.create(12345L);

boolean sameSequence = true;
for (int i = 0; i < 10; i++) {
    sameSequence &= first.nextInt() == second.nextInt();
}
System.out.println(sameSequence); // true
```

Với **cùng thuật toán hỗ trợ seed `long`**, cùng seed và cùng **chuỗi lời gọi**, hai bộ sinh bắt đầu từ cùng trạng thái và cho các giá trị tương ứng giống nhau. Điều này có ích khi tái tạo một phép mô phỏng hoặc lỗi kiểm thử.

Phải hiểu đúng giới hạn của factory: API có ba overload `create()`, `create(long seed)` và `create(byte[] seed)`. Đặc tả cho phép `create(long)` **quay về hành vi tương tự `create()` nếu thuật toán không hỗ trợ seed kiểu `long`**; overload nhận `byte[]` cũng có điều kiện hỗ trợ riêng. Vì vậy, muốn có reproducibility đáng tin cậy, chọn thuật toán đã xác minh hỗ trợ kiểu seed sử dụng thay vì giả định mọi provider đều giống nhau.

## 4. Phạm vi số, kiểu dữ liệu và stream

Điểm mạnh của interface chung là API nhất quán cho các kiểu số và nhiều dạng phân phối:

| Lời gọi | Miền kết quả / ý nghĩa |
| --- | --- |
| `rng.nextInt()` | Giá trị `int` được sinh từ toàn bộ miền giá trị `int`. |
| `rng.nextInt(10)` | Số nguyên từ **0 đến 9**, chặn trên 10 **không** nằm trong miền. |
| `rng.nextInt(5, 10)` | Số nguyên từ **5 đến 9**, tức `[5, 10)`. |
| `rng.nextLong(100L, 200L)` | Giá trị `long` thuộc `[100, 200)`. |
| `rng.nextDouble()` | Giá trị `double` trong `[0.0, 1.0)`. |
| `rng.nextDouble(2.0, 5.0)` | Giá trị `double` thuộc `[2.0, 5.0)`. |
| `rng.nextBoolean()` | Giá trị logic `true` hoặc `false`. |
| `rng.nextGaussian()` | Giá trị theo phân phối chuẩn với kỳ vọng 0, độ lệch chuẩn 1. |
| `rng.nextExponential()` | Giá trị không âm theo phân phối mũ với kỳ vọng 1. |
| `rng.ints(5, 1, 7)` | `IntStream` **gồm 5 giá trị**, mỗi giá trị trong `[1, 7)`. |
| `rng.longs(...)`, `rng.doubles(...)` | Các biến thể stream cho `long` và `double`. |

**Chặn trái inclusive, chặn phải exclusive** đối với các overload nhận `origin` và `bound`. Đây là cách thiết kế thuận tiện: sinh mặt xúc xắc sáu mặt bằng `nextInt(1, 7)`, thay vì `nextInt(1, 6)` khiến mặt 6 không bao giờ xuất hiện.

```java
int die = rng.nextInt(1, 7);
int zeroToNine = rng.nextInt(10);

int[] fiveDice = rng.ints(5, 1, 7).toArray();
double[] tenFractions = rng.doubles(10).toArray();
```

Các overload stream nhận `streamSize` có số phần tử hữu hạn. Các overload không truyền kích thước tạo stream có thể hoạt động gần như vô hạn, cần giới hạn bằng `limit(n)` hoặc short-circuit operation. **Đừng thu thập stream vô hạn vào một danh sách/array**.

Tham số không hợp lệ có thể dẫn đến `IllegalArgumentException`: `rng.nextInt(0)` có bound không dương; `rng.nextInt(10, 10)` có origin không nhỏ hơn bound; `rng.ints(-1)` có số phần tử âm. Xác thực dữ liệu đầu vào trước khi gọi.

Số được sinh từ `nextDouble()` không phải số thập phân chính xác dành cho kế toán; việc nhân với 100 và chuyển sang `BigDecimal` cũng không tự biến nó thành nguồn số ngẫu nhiên có thể kiểm toán hoặc tài chính an toàn.

## 5. RandomGeneratorFactory: khám phá và đánh giá thuật toán

Factory cung cấp metadata giúp chọn thuật toán dựa theo **yêu cầu sử dụng**.

```java
RandomGeneratorFactory<RandomGenerator> factory =
        RandomGeneratorFactory.of("L64X128MixRandom");

System.out.println(factory.name());       // L64X128MixRandom
System.out.println(factory.isSplittable()); // true
```

Các phương thức metadata quan trọng:

| Phương thức | Ý nghĩa |
| --- | --- |
| `name()` | Tên định danh thuật toán mà factory tạo ra. |
| `group()` | Nhóm thuật toán, chẳng hạn LXM hay Legacy. |
| `stateBits()` | Số bit biểu diễn trạng thái của thuật toán; **không đồng nghĩa số bit entropy của seed**. |
| `period()` | Chu kỳ thuật toán dưới dạng `BigInteger`; trả `BigInteger.ZERO` nếu không xác định được. |
| `equidistribution()` | Thông tin mức `k`-equidistribution của thuật toán. |
| `isSplittable()` | Có hỗ trợ tạo bộ sinh mới qua cơ chế `split` không. |
| `isJumpable()` | Có hỗ trợ nhảy xa trong chu kỳ không. |
| `isLeapable()` | Có hỗ trợ bước nhảy rất xa không. |
| `isArbitrarilyJumpable()` | Có hỗ trợ nhảy tùy khoảng không. |
| `isStreamable()` | Có hỗ trợ stream các **generator** mới qua giao diện chuyên biệt không. |
| `isDeprecated()` | Thuật toán đã bị đánh dấu không nên tiếp tục dùng hay chưa. |
| `isStatistical()`, `isStochastic()`, `isHardware()` | Các thuộc tính phân loại nguồn hoặc cách tạo số. |

**Metadata không phải thang điểm chất lượng toàn diện.** Một `period` dài hoặc `stateBits()` lớn không chứng minh an toàn mật mã, phù hợp mọi mô phỏng hay bảo đảm dãy độc lập tuyệt đối giữa các seed.

### Lọc các factory thay vì đoán thuật toán

Ví dụ chọn một factory không deprecated, hỗ trợ split và có ít nhất 128 bit trạng thái:

```java
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

RandomGeneratorFactory<RandomGenerator> chosen = RandomGeneratorFactory.all()
        .filter(f -> !f.isDeprecated())
        .filter(RandomGeneratorFactory::isSplittable)
        .filter(f -> f.stateBits() >= 128)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
                "Không tìm thấy thuật toán phù hợp"));

System.out.println(chosen.name());
RandomGenerator generator = chosen.create();
```

Đây là **đoạn đặt trong thân phương thức**, không phải chương trình độc lập. `all()` đã loại các thuật toán deprecated theo đặc tả Java 17, nên điều kiện `!f.isDeprecated()` ở đây chỉ thể hiện rõ yêu cầu. Không cam kết thuật toán được chọn là gì: danh sách thuật toán khả dụng có thể khác giữa JDK/provider, và thứ tự stream không nên dùng để xác lập một lựa chọn có tính lâu dài.

Nếu **tái lập kết quả** quan trọng, hãy lưu **tên thuật toán + seed + phiên bản/thông số cần thiết + lịch trình gọi API**. Không nên lọc rồi gọi `findFirst()` tại mỗi lần chạy và mặc định lựa chọn luôn giống nhau.

### Factory mặc định

`RandomGeneratorFactory.getDefault()` trả factory của thuật toán thỏa tối thiểu **`stateBits() >= 64`**, nhưng không cam kết tên thuật toán giữ nguyên qua các phiên bản. `RandomGenerator.getDefault()` có chủ đích tương tự ở mức instance. Đừng hardcode stdout các giá trị từ bộ sinh mặc định.

## 6. Các nhóm thuật toán và cách chọn trong thực tế

Java 17 mô tả các nhóm chính:

- **Legacy**: các bộ sinh lâu đời như `Random`, `ThreadLocalRandom`, `SplittableRandom` và `SecureRandom` trong hệ sinh thái Java. Một phần vẫn thích hợp do yêu cầu tương thích hoặc trường hợp chuyên biệt.
- **LXM**: kết hợp ý tưởng *linear congruential generator* và *xor-based generator*, với biến thể như `L64X128MixRandom`, `L64X256MixRandom`. Phù hợp nhiều workload mô phỏng và hỗ trợ splitting ở các biến thể tương ứng.
- **Xoroshiro/Xoshiro**: gồm các thuật toán như `Xoroshiro128PlusPlus` và `Xoshiro256PlusPlus`, cung cấp đặc tính hiệu suất và hỗ trợ jumping trong các implementation Java 17 tương ứng.

Chọn theo vấn đề:

| Nhu cầu | Hướng lựa chọn |
| --- | --- |
| Code thông thường, không ràng buộc thuật toán | `RandomGenerator.getDefault()` hoặc factory mặc định. |
| Phép mô phỏng cần tái lập dãy | Tên thuật toán cố định + seed cố định + trật tự gọi được kiểm soát. |
| Nhiều tác vụ song song cần các bộ sinh riêng | Thuật toán hỗ trợ `SplittableGenerator` hoặc `JumpableGenerator`; thiết kế phân bổ generator theo worker/task. |
| Cần tương thích chuỗi số cũ của `java.util.Random` | Giữ `Random`/thuật toán tương thích, thử lại các trường hợp quan trọng. |
| Token, mã xác thực, khóa, nonce nhạy cảm bảo mật | `SecureRandom`; không chọn LXM/Xoshiro chỉ vì tốc độ hoặc chu kỳ lớn. |

Trong phần lớn bài tập số học, nên bắt đầu với interface `RandomGenerator`, sau đó mới bổ sung yêu cầu về seed, thuật toán và splitting nếu bài toán thực sự cần. Một thuật toán phức tạp không tự động làm phần mềm “random hơn” theo mọi ý nghĩa.

## 7. Chương trình Java 17+ hoàn chỉnh và đầu ra có thể kiểm tra

Lưu chương trình sau vào **`RandomGeneratorDemo.java`**:

```java
import java.util.Arrays;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class RandomGeneratorDemo {
    public static void main(String[] args) {
        RandomGeneratorFactory<RandomGenerator> factory =
                RandomGeneratorFactory.of("L64X128MixRandom");

        RandomGenerator first = factory.create(2026L);
        RandomGenerator second = factory.create(2026L);

        boolean repeatable = true;
        for (int i = 0; i < 20; i++) {
            repeatable &= first.nextInt() == second.nextInt();
        }

        RandomGenerator rng = factory.create(42L);
        int die = rng.nextInt(1, 7);
        long sampleLong = rng.nextLong(100L, 200L);
        double fraction = rng.nextDouble();

        int[] fiveDice = rng.ints(5, 1, 7).toArray();
        boolean validDice = Arrays.stream(fiveDice)
                .allMatch(n -> n >= 1 && n < 7);

        RandomGeneratorFactory<RandomGenerator> defaultFactory =
                RandomGeneratorFactory.getDefault();

        System.out.println("algorithm = " + factory.name());
        System.out.println("same seed and calls = " + repeatable);
        System.out.println("splittable = " + factory.isSplittable());
        System.out.println("die in [1, 7) = " + (die >= 1 && die < 7));
        System.out.println("long in [100, 200) = "
                + (sampleLong >= 100 && sampleLong < 200));
        System.out.println("double in [0, 1) = "
                + (fraction >= 0.0 && fraction < 1.0));
        System.out.println("stream size = " + fiveDice.length);
        System.out.println("stream bounds valid = " + validDice);
        System.out.println("default state bits >= 64 = "
                + (defaultFactory.stateBits() >= 64));
    }
}
```

Biên dịch và chạy trong thư mục chứa file, với **JDK 17 trở lên**:

```bash
javac RandomGeneratorDemo.java
java RandomGeneratorDemo
```

Đầu ra kỳ vọng với API và thuật toán được chỉ định:

```text
algorithm = L64X128MixRandom
same seed and calls = true
splittable = true
die in [1, 7) = true
long in [100, 200) = true
double in [0, 1) = true
stream size = 5
stream bounds valid = true
default state bits >= 64 = true
```

Ví dụ cố tình in **tên thuật toán và các điều kiện đúng/sai** thay cho giá trị cụ thể của từng lần rút. Giá trị random vẫn khác nhau giữa các lần gọi hoặc seed, nhưng mọi lần rút trong đoạn mã đều phải thỏa miền đã khai báo. Hai instance có cùng thuật toán và seed `2026L`, lại được gọi cùng số lần theo cùng thứ tự, cho dãy khớp nhau.

Nếu thay bằng `getDefault()` hoặc để factory chọn thuật toán động qua `all()`, không được dựa vào danh tính thuật toán hay chuỗi số mặc định như một hợp đồng cố định của ứng dụng.

## 8. Splittable và Jumpable: tạo luồng số cho tính toán song song

Khi nhiều tác vụ cùng dùng bộ sinh giả ngẫu nhiên, **một `RandomGenerator` dùng chung có thể không thread-safe**. Giao diện cơ sở không bảo đảm đồng bộ. Sử dụng một generator riêng cho mỗi worker hoặc một chiến lược phân bổ phù hợp là cách tiếp cận thường gặp.

Java 17 giới thiệu các giao diện lồng trong `RandomGenerator` để mô tả khả năng đặc biệt:

- `RandomGenerator.SplittableGenerator`: tạo một generator mới thông qua `split()`, phục vụ việc phân nhánh công việc.
- `RandomGenerator.JumpableGenerator`: có thao tác `jump()` để tiến tới một vị trí cách xa trong dãy trạng thái.
- `RandomGenerator.LeapableGenerator` và `ArbitrarilyJumpableGenerator`: các biến thể bổ sung về khoảng nhảy.
- `RandomGenerator.StreamableGenerator`: cung cấp cách tạo stream gồm nhiều generator.

Các khả năng này là **tùy thuật toán**, không có trên mọi `RandomGenerator`. Chỉ ép kiểu khi đã xác thực loại hoặc biết chắc thuật toán.

### Ví dụ split() với thuật toán LXM

Đây là chương trình Java 17+ độc lập, lưu thành `SplitDemo.java`:

```java
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class SplitDemo {
    public static void main(String[] args) {
        RandomGeneratorFactory<RandomGenerator.SplittableGenerator> factory =
                RandomGeneratorFactory.of("L64X128MixRandom");

        RandomGenerator.SplittableGenerator parent =
                factory.create(2026L);
        RandomGenerator.SplittableGenerator child = parent.split();

        int parentValue = parent.nextInt(1, 7);
        int childValue = child.nextInt(1, 7);

        System.out.println("parent valid = "
                + (parentValue >= 1 && parentValue < 7));
        System.out.println("child valid = "
                + (childValue >= 1 && childValue < 7));
        System.out.println("different objects = " + (parent != child));
    }
}
```

Biên dịch và chạy:

```bash
javac SplitDemo.java
java SplitDemo
```

Kết quả:

```text
parent valid = true
child valid = true
different objects = true
```

`split()` trả về một generator riêng có thể được chuyển cho tác vụ khác. **Không hiểu `split()` là chứng minh toán học tuyệt đối rằng mọi dãy sinh ra đều độc lập trong mọi tình huống.** Ý tưởng của thuật toán là hạn chế chồng lặp và hỗ trợ chia công việc. Khi cần tính tái lập của mô phỏng chạy song song, phải kiểm soát cả **thứ tự tách generator, ánh xạ generator vào task và lịch gọi**, vì thay đổi lịch xử lý có thể đổi phần dãy được từng task sử dụng.

Tương tự, nếu cần `jump()`, hãy chọn thuật toán có `factory.isJumpable() == true` rồi sử dụng giao diện `RandomGenerator.JumpableGenerator`. Gọi `jump()` không phải “xin số ngẫu nhiên mạnh hơn”; nó thay đổi vị trí generator trong chu trình theo quy tắc thuật toán, hỗ trợ phân hoạch công việc.

### ThreadLocalRandom liên quan thế nào?

`ThreadLocalRandom.current()` vẫn hữu ích trong code nhiều luồng khi mỗi luồng cần truy cập bộ sinh của chính luồng đó. Nó là API lâu đời và triển khai `RandomGenerator`. Với kiến trúc chia tác vụ linh hoạt hoặc fork/join, các generator hỗ trợ `split()` có thể thích hợp hơn. Việc chia sẻ một instance bất kỳ không được mặc định an toàn chỉ vì biến có kiểu `RandomGenerator`.

## 9. Seed, tính tái lập và giới hạn cần hiểu

### 9.1. Tại sao seed quan trọng?

Seed giúp khởi tạo trạng thái ban đầu của generator. Cùng thuật toán và seed phù hợp, kết hợp cùng lịch gọi hàm, thường cho cùng dãy số, rất hữu ích khi tái hiện lỗi. Ví dụ:

```java
RandomGeneratorFactory<RandomGenerator> factory =
        RandomGeneratorFactory.of("L64X128MixRandom");

RandomGenerator a = factory.create(1234L);
RandomGenerator b = factory.create(1234L);

// Dãy nextLong() của a và b sẽ khớp nếu thứ tự lời gọi tương ứng như nhau.
```

Nếu một bên gọi thêm `nextInt()` trước, trạng thái có thể tiến khác; các giá trị tiếp theo không còn khớp cặp theo kỳ vọng. Tương tự, gọi stream và gọi đơn lẻ với cùng số lượng mẫu không luôn được đặc tả phải cho **cùng dãy giá trị theo từng vị trí**; Java chỉ bảo đảm các giá trị tuân theo cùng kiểu chọn phân phối/phạm vi. Không dùng sự tương đương giữa phương thức stream và vòng lặp đơn lẻ làm hợp đồng tái lập nếu chưa được thuật toán/implementation bảo đảm.

### 9.2. Seed cố định có an toàn không?

**Không.** Seed cố định làm dãy dễ tái hiện và có thể dễ đoán. Ngay cả seed từ đồng hồ hiện tại cũng không bảo đảm đủ entropy cho bảo mật. Dùng seed cố định cho kiểm thử/mô phỏng có chủ đích; không dùng để sinh session token, mật khẩu, mã khôi phục hoặc khóa.

`RandomGeneratorFactory.create()` không truyền seed thường phù hợp khi không yêu cầu lặp lại cùng dãy, nhưng **không biến thuật toán thường thành CSPRNG**. Bảo mật dựa vào thuật toán và nguồn entropy được thiết kế cho mục đích đó; dùng `SecureRandom`.

### 9.3. Ghi chép gì để tái hiện kết quả?

Nếu một phép mô phỏng phải chạy lại để so sánh, nên ghi:

1. Tên thuật toán, ví dụ `L64X128MixRandom`.
2. Giá trị và kiểu seed đã dùng.
3. Phiên bản JDK/provider khi khả năng tái hiện chéo môi trường là yêu cầu.
4. Trình tự gọi phương thức, số lượng giá trị, các tham số bound/origin.
5. Với chương trình song song, sơ đồ `split()`, số worker và cách phân phối task.

**Seed không phải bản snapshot đầy đủ của trạng thái sau nhiều lần rút**. Nó mô tả cách khởi tạo; muốn quay lại giữa dãy phải tái thực hiện đúng các bước hoặc có cơ chế lưu trạng thái được cung cấp/kiểm chứng. Không tự giả định mọi implementation cho phép serialization, hoặc có API khôi phục trạng thái.

## 10. Những lỗi thường gặp khi chuyển từ Random sang API Java 17

1. **Nhập nhầm package.** `RandomGenerator` và `RandomGeneratorFactory` nằm trong `java.util.random`, khác `java.util.Random`.
2. **Cố gọi `new RandomGenerator()`.** Đây là interface; tạo bằng `getDefault()`, `of(name)` hoặc factory.
3. **Nhầm chặn trên của range.** `nextInt(1, 7)` sinh 1–6, không sinh 7; `nextInt(6)` sinh 0–5.
4. **Dùng `getDefault()` rồi kỳ vọng cùng thuật toán ở mọi JDK.** Thuật toán mặc định có thể thay đổi. Cố định `name` khi cần.
5. **Dùng cùng seed nhưng thay đổi chuỗi gọi.** Khả năng tái hiện phụ thuộc thuật toán và thứ tự tiêu thụ dãy.
6. **Cho rằng mọi thuật toán hỗ trợ seed `long` giống nhau.** Factory có cơ chế fallback nếu thuật toán không hỗ trợ kiểu seed yêu cầu. Chọn thuật toán cụ thể đã kiểm chứng.
7. **Gọi `factory.isSplittable()` và hiểu rằng interface cơ sở có `split()`.** `split()` thuộc `RandomGenerator.SplittableGenerator`; cần kiểu phù hợp.
8. **Chia sẻ generator giữa nhiều thread mà không biết thread safety.** `RandomGenerator` không cam kết thread-safe; tổ chức một generator riêng cho worker hoặc dùng cơ chế chuyên dụng.
9. **Dùng PRNG thông thường cho bảo mật.** Chọn `SecureRandom` cho token/khóa/bí mật nhạy cảm, bất kể chu kỳ PRNG dài ra sao.
10. **Tạo stream vô hạn rồi thu thập toàn bộ.** Với `ints()`/`doubles()` không kích thước, dùng `limit(n)` hoặc quá trình tiêu thụ có điểm dừng.
11. **Cho rằng `stateBits` = entropy của seed hoặc “mức an toàn”.** Đây là kích thước trạng thái, không phải thước đo chống dự đoán mật mã.
12. **Kiểm thử bằng giá trị ngẫu nhiên hardcode.** Với code không dùng named algorithm + seed và thứ tự lời gọi cố định, hãy kiểm tra bất biến như range, độ dài stream, uniqueness theo yêu cầu thống kê phù hợp; tránh test chỉ vì “hôm nay số này khác số kia”.

## 11. Ứng dụng: mô phỏng xúc xắc có thể tái lập

Trong bài kiểm thử hoặc mô phỏng, có thể truyền `RandomGenerator` vào hàm thay vì khởi tạo trực tiếp, giúp test kiểm soát đầu vào ngẫu nhiên.

```java
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class DiceSimulation {
    static int roll(RandomGenerator generator) {
        return generator.nextInt(1, 7);
    }

    static int totalOfThreeRolls(RandomGenerator generator) {
        return roll(generator) + roll(generator) + roll(generator);
    }

    public static void main(String[] args) {
        var factory = RandomGeneratorFactory.of("L64X128MixRandom");

        RandomGenerator first = factory.create(17L);
        RandomGenerator again = factory.create(17L);

        int result1 = totalOfThreeRolls(first);
        int result2 = totalOfThreeRolls(again);

        System.out.println("same total = " + (result1 == result2));
        System.out.println("total in [3, 18] = "
                + (result1 >= 3 && result1 <= 18));
    }
}
```

Lưu `DiceSimulation.java` rồi chạy:

```bash
javac DiceSimulation.java
java DiceSimulation
```

Đầu ra:

```text
same total = true
total in [3, 18] = true
```

Hàm `roll()` chỉ phụ thuộc giao diện `RandomGenerator`, nên dễ đổi thuật toán hoặc truyền generator đã được cấu hình từ ngoài. Trong ứng dụng lớn hơn, đây cũng là cách tách **logic nghiệp vụ mô phỏng** khỏi **chính sách chọn thuật toán và quản lý seed**.

## 12. Câu hỏi tự kiểm tra

**Câu 1. `RandomGenerator` và `RandomGeneratorFactory` khác nhau ở đâu?**

`RandomGenerator` cung cấp phương thức sinh giá trị; `RandomGeneratorFactory` lựa chọn thuật toán, truy vấn metadata và tạo instance.

**Câu 2. Tại sao `rng.nextInt(1, 7)` phù hợp để mô phỏng xúc xắc?**

Vì giá trị thuộc miền `[1, 7)`, tức 1 đến 6, mỗi giá trị khả dĩ thuộc tập sáu mặt.

**Câu 3. Nếu dùng `RandomGenerator.getDefault()` với seed ngầm, có thể kiểm thử bằng một dãy cố định bất biến không?**

Không nên. Thuật toán mặc định có thể thay đổi và trạng thái khởi tạo cũng không được cố định qua các lần chạy.

**Câu 4. Muốn tái hiện dãy trong test cần gì?**

Ít nhất phải kiểm soát thuật toán hỗ trợ seed, giá trị seed và **đúng thứ tự gọi các phương thức**; nếu chạy song song, cần thêm chính sách chia và phân phối dãy.

**Câu 5. `factory.isSplittable()` dùng để làm gì?**

Để xác định factory tạo thuật toán hỗ trợ khả năng tách thành các generator phục vụ phân nhánh công việc; lời gọi `split()` thuộc `RandomGenerator.SplittableGenerator`.

**Câu 6. Một thuật toán có `period()` rất lớn có dùng sinh khóa bảo mật được không?**

Không thể kết luận như vậy. Period lớn và stateBits nhiều không bảo đảm khả năng chống dự đoán; dùng `SecureRandom` theo tiêu chuẩn bảo mật.

**Câu 7. `factory.create(123L)` có đảm bảo seed được áp dụng cho mọi thuật toán không?**

Không. Theo hợp đồng factory, khi thuật toán không hỗ trợ kiểu `long`, lời gọi có thể fallback sang `create()` không tham số.

**Câu 8. `RandomGeneratorFactory.all()` trả về gì?**

Một `Stream<RandomGeneratorFactory<RandomGenerator>>` của các thuật toán khả dụng (không gồm thuật toán bị đánh dấu deprecated trong Java 17), dùng để lọc hoặc xem metadata; tập hợp thực tế có thể khác theo JDK/provider.

**Câu 9. Vì sao không nên dùng chung một generator bất kỳ giữa nhiều tác vụ?**

Vì `RandomGenerator` không cam kết thread-safe; concurrent use có thể dẫn tới race hoặc lịch tiêu thụ dãy không tái hiện được. Tùy workload, dùng generator riêng, `split()`/`jump()` hoặc `ThreadLocalRandom`.

**Câu 10. Khi nào nên truyền `RandomGenerator` làm tham số hàm?**

Khi muốn tách logic sử dụng số ngẫu nhiên khỏi logic chọn thuật toán/seed, giúp dễ thay implementation và tái hiện test.

## 13. Tài liệu tham khảo chính thức

- [Oracle Java SE 17 — RandomGenerator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/RandomGenerator.html): giao diện, giới hạn phạm vi, phương thức sinh số và stream.
- [Oracle Java SE 17 — RandomGeneratorFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/RandomGeneratorFactory.html): factory, `all()`, `of()`, `create()`, thông tin thuật toán và giới hạn seed.
- [Oracle Java SE 17 — java.util.random](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/package-summary.html): nhóm thuật toán và gợi ý lựa chọn.
- [OpenJDK JEP 356 — Enhanced Pseudo-Random Number Generators](https://openjdk.org/jeps/356): mục tiêu thay đổi API trong Java 17.

**Ghi nhớ:** Từ Java 17, **`RandomGenerator` là hợp đồng sinh số**, còn **`RandomGeneratorFactory` là cơ chế chọn và tạo thuật toán**. Hãy chọn rõ seed/thuật toán khi cần tái lập, chọn `SplittableGenerator` hoặc `JumpableGenerator` cho workload có yêu cầu tương ứng, chú ý miền `[origin, bound)` và **luôn dùng `SecureRandom` cho dữ liệu nhạy cảm bảo mật**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-007">Các khoảng origin, bound và quy tắc cận trên loại trừ</a>

<details>
<summary>Xem chi tiết</summary>

# Các khoảng origin, bound và quy tắc cận trên loại trừ khi tạo số ngẫu nhiên trong Java

## 1. Hiểu đúng khoảng [origin, bound)

Trong các API sinh số ngẫu nhiên có tham số `origin` và `bound` của Java, quy tắc trọng tâm là:

**`origin` được lấy (inclusive), còn `bound` bị loại trừ (exclusive).**

Ký hiệu toán học: **[origin, bound)**, tương đương điều kiện:

~~~text
origin <= result < bound
~~~

Với **số nguyên**, tập giá trị có thể trả về hữu hạn và liệt kê được. Ví dụ:

| Lệnh Java 17 | Khoảng chính xác | Các giá trị `int` có thể có |
|---|---|---|
| `rng.nextInt(0, 5)` | [0, 5) | 0, 1, 2, 3, 4 |
| `rng.nextInt(1, 7)` | [1, 7) | 1, 2, 3, 4, 5, 6 |
| `rng.nextInt(-3, 4)` | [-3, 4) | -3, -2, -1, 0, 1, 2, 3 |
| `rng.nextInt(7, 8)` | [7, 8) | chỉ 7 |
| `rng.nextInt(-5, 0)` | [-5, 0) | -5, -4, -3, -2, -1 |
| `rng.nextInt(5, 5)` | khoảng rỗng | ném `IllegalArgumentException` |

Với [0, 5), số 5 **không thể** được sinh bởi đúng lời gọi này. Với [-3, 4), số -3 **có thể** xuất hiện và số 4 **không thể** xuất hiện. Quy tắc vẫn giữ nguyên khi origin âm, dương hoặc bằng 0.

Khi biểu diễn bằng số học trên tập số nguyên, số phần tử của khoảng [origin, bound) là `bound - origin`, **được hiểu theo toán học**. Nếu tính hiệu này bằng phép trừ `int` trong Java, phép toán có thể overflow; không nên lấy công thức rồi mặc định kiểu `int` luôn chứa được kết quả.

### Vì sao chọn cận trên loại trừ?

Khoảng nửa mở giúp tính kích thước và ghép đoạn rõ ràng. Nếu [a, b) và [b, c) được ghép, chúng không trùng giá trị b và bao phủ [a, c). Phân đoạn dãy bằng chỉ số cũng dùng quy tắc này: một mảng độ dài n có chỉ số [0, n), nên `nextInt(n)` phù hợp để chọn một chỉ số hợp lệ khi **n > 0**.

Đừng nhầm chữ “bound” với “giá trị lớn nhất có thể trả về”. Trong `nextInt(origin, bound)`, nếu khoảng không rỗng, số nguyên lớn nhất có thể trả về là **bound - 1**, trừ khi đây chỉ là cách nói toán học để diễn giải; trong code vẫn phải chú ý overflow khi thao tác số cực trị.

## 2. Các API phổ biến trong Java 17+

Java 17 có interface `java.util.random.RandomGenerator`, cung cấp cách gọi nhất quán trên các generator khác nhau. Một `java.util.Random` có thể dùng qua interface này:

~~~java
import java.util.Random;
import java.util.random.RandomGenerator;

RandomGenerator rng = new Random(2026L);
int roll = rng.nextInt(1, 7); // 1..6
~~~

Đoạn trên là **minh họa đặt bên trong phương thức**, không có `main`; ví dụ chương trình hoàn chỉnh nằm ở phần 5.

| API | Khoảng trả về | Ràng buộc chính |
|---|---|---|
| `rng.nextInt()` | Một giá trị `int` bất kỳ | Không có cận origin/bound |
| `rng.nextInt(bound)` | [0, bound) | `bound > 0` |
| `rng.nextInt(origin, bound)` | [origin, bound) | `origin < bound` |
| `rng.nextLong(origin, bound)` | [origin, bound) trên kiểu `long` | `origin < bound` |
| `rng.nextDouble()` | [0.0, 1.0) | Giá trị dấu phẩy động |
| `rng.nextDouble(origin, bound)` | [origin, bound) trên kiểu `double` | Hai cận hữu hạn và `origin < bound` |
| `rng.nextFloat(origin, bound)` | [origin, bound) trên kiểu `float` | Hai cận hữu hạn và `origin < bound` |
| `random.ints(size, origin, bound)` | Stream gồm `size` số `int` thuộc [origin, bound) | `size >= 0` và `origin < bound` |
| `random.longs(size, origin, bound)` | Stream `long` có cùng quy tắc nửa mở | Cận hợp lệ, size không âm |
| `random.doubles(size, origin, bound)` | Stream `double` có cùng quy tắc nửa mở | Cận hữu hạn, size không âm |

Các dòng `rng` giả định `rng` thuộc interface `RandomGenerator`. `Random` có phương thức `nextInt(int bound)` và triển khai interface `RandomGenerator` ở Java 17; vì vậy các overload `nextInt(origin, bound)`, `nextLong(origin, bound)` có thể được gọi qua interface. `ThreadLocalRandom.current()` và `SplittableRandom` cũng có các overload bounded phù hợp.

Ví dụ chọn cách sử dụng:

~~~java
int dice = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 7);
long idPart = java.util.concurrent.ThreadLocalRandom.current()
        .nextLong(1_000L, 10_000L);
~~~

Đây là đoạn minh họa trong một phương thức: `dice` thuộc [1, 7), `idPart` thuộc [1000, 10000). Đây **không phải** cách tạo mã bí mật; các generator thông thường được thiết kế cho tác vụ giả ngẫu nhiên, không mặc định an toàn trước đối thủ.

Khi yêu cầu phải giữ một hạt giống (`seed`) có thể tái hiện test, `new Random(seed)` là lựa chọn dễ hiểu. `ThreadLocalRandom.current()` phù hợp khi nhiều luồng cần tạo số ngẫu nhiên, nhưng không có API đặt seed trực tiếp theo cách dùng thông thường. `SplittableRandom` hỗ trợ chia nhánh generator cho các tác vụ tính toán; các generator có thể tạo chuỗi khác nhau dù đầu vào seed giống nhau.

## 3. nextInt(bound) khác nextInt(origin, bound) thế nào?

Với `nextInt(bound)`, **origin ngầm định là 0**. `nextInt(6)` sinh một số trong {0, 1, 2, 3, 4, 5}. Để mô phỏng xúc xắc từ 1 đến 6:

~~~java
int diceA = rng.nextInt(6) + 1; // 1..6, bound phải dương
int diceB = rng.nextInt(1, 7); // 1..6, trực tiếp nêu khoảng
~~~

Hai công thức có cùng tập giá trị có thể trả về, nhưng không được kỳ vọng cùng kết quả khi gọi liên tiếp vì mỗi lời gọi tiêu thụ trạng thái generator.

Nếu muốn số nguyên trong [-5, 5), **không thể** truyền bound âm vào `nextInt(bound)` rồi chờ nhận giá trị âm:

~~~java
int signed = rng.nextInt(-5, 5); // -5..4
// rng.nextInt(-5);             // IllegalArgumentException
~~~

Với origin khác 0, đặc biệt có số âm hoặc sát cực trị của kiểu số, ưu tiên overload hai cận để bộc lộ rõ ý nghĩa. Không chuyển khoảng bằng `rng.nextInt(bound - origin) + origin` một cách máy móc: nếu phép trừ int overflow, kích thước khoảng tính ra sai và lời gọi có thể thất bại hoặc hành vi khác yêu cầu.

## 4. Đầu vào không hợp lệ và những trường hợp biên

Các overload bị ràng buộc bởi **khoảng có ít nhất một giá trị hợp lệ**:

- `nextInt(bound)` cần `bound > 0`. `bound = 0` hay âm đều sai; `nextInt(0)` không có giá trị hợp lệ.
- `nextInt(origin, bound)` và `nextLong(origin, bound)` cần `origin < bound`. Khoảng [7, 7) rỗng; [8, 7) ngược cận. Hai trường hợp đều ném `IllegalArgumentException`.
- Với `nextDouble(origin, bound)` và `nextFloat(origin, bound)`, hai cận phải hữu hạn, không phải NaN/Infinity, và thỏa origin < bound.
- `size` trong các overload tạo stream hữu hạn phải không âm. `size = 0` tạo stream rỗng, không phải lỗi nếu hai cận hợp lệ.

**Một phần tử trong khoảng số nguyên** không giống khoảng rỗng. [7, 8) luôn trả 7; [7, 7) không trả giá trị nào và bị từ chối. Đây là test tốt cho lỗi nhầm inclusive/exclusive.

Nếu API của bạn nhận `origin` và `bound` từ người dùng, hãy xác thực sớm và trả lỗi dễ hiểu: đầu vào cần đúng kiểu, khoảng không rỗng, miền giá trị không vi phạm quy tắc nghiệp vụ. Sau xác thực, vẫn để API chuẩn thực hiện sinh số ngẫu nhiên thay vì tự thay đổi cận không có cơ sở.

## 5. Chương trình Java 17+ có kiểm chứng giới hạn

Lưu thành **`OriginBoundDemo.java`**. Chương trình không in ra các số ngẫu nhiên cụ thể, vì giá trị được rút là không cố định theo ý nghĩa của ví dụ; thay vào đó nó **kiểm tra bất biến và in kết quả ổn định**. Kiểm tra này chứng minh phạm vi của các giá trị *đã lấy mẫu*, không chứng minh xác suất đồng đều của toàn bộ generator.

~~~java
import java.util.Random;
import java.util.random.RandomGenerator;

public class OriginBoundDemo {
    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Range invariant violated");
        }
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Đúng hợp đồng: tham số khoảng không hợp lệ.
        }
    }

    // Hỗ trợ toàn bộ miền int, kể cả upper = Integer.MAX_VALUE.
    private static int nextIntInclusive(RandomGenerator rng, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min must not exceed max");
        }
        long upperExclusive = (long) max + 1L;
        long sampled = rng.nextLong((long) min, upperExclusive);
        return Math.toIntExact(sampled);
    }

    public static void main(String[] args) {
        RandomGenerator rng = new Random(2026L);

        for (int i = 0; i < 10_000; i++) {
            int x = rng.nextInt(-3, 4);
            long y = rng.nextLong(-20L, 21L);
            double z = rng.nextDouble(-0.5, 2.5);

            check(x >= -3 && x < 4);
            check(y >= -20L && y < 21L);
            check(z >= -0.5 && z < 2.5);
        }
        System.out.println("Half-open ranges: PASS");

        check(rng.nextInt(7, 8) == 7);
        check(rng.nextInt(Integer.MAX_VALUE - 1,
                          Integer.MAX_VALUE) == Integer.MAX_VALUE - 1);
        check(nextIntInclusive(rng, Integer.MAX_VALUE,
                                   Integer.MAX_VALUE) == Integer.MAX_VALUE);
        check(nextIntInclusive(rng, Integer.MIN_VALUE,
                                   Integer.MIN_VALUE) == Integer.MIN_VALUE);
        System.out.println("Boundary cases: PASS");

        expectInvalid(() -> rng.nextInt(0));
        expectInvalid(() -> rng.nextInt(5, 5));
        expectInvalid(() -> rng.nextInt(6, 5));
        expectInvalid(() -> rng.nextDouble(Double.NaN, 1.0));
        expectInvalid(() -> nextIntInclusive(rng, 9, 8));
        System.out.println("Invalid arguments: PASS");

        check(rng.ints(100, -3, 4)
                 .allMatch(value -> value >= -3 && value < 4));
        System.out.println("Bounded stream: PASS");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac OriginBoundDemo.java
java OriginBoundDemo
~~~

Stdout:

~~~text
Half-open ranges: PASS
Boundary cases: PASS
Invalid arguments: PASS
Bounded stream: PASS
~~~

Trong `nextIntInclusive`, hàm có quy tắc **cả hai cận đều được lấy**: [min, max]. Vì tham số `max` là `int`, phép cộng `(long) max + 1L` an toàn trong `long`, kể cả khi max bằng `Integer.MAX_VALUE`. Số ngẫu nhiên được tạo ở dạng `long` trong [min, max + 1), rồi chuyển chính xác về `int` bằng `Math.toIntExact`; vì kết quả không vượt giới hạn `int` theo tiền điều kiện, chuyển đổi thành công. Đây là phiên bản tổng quát cho khoảng nguyên đóng kiểu int, không phải công thức để xử lý mọi khoảng kiểu long.

## 6. Đổi [min, max] sang [origin, bound) mà không bị off-by-one

Bài toán kinh điển: “lấy ngẫu nhiên từ 1 đến 6 **bao gồm cả 6**”. Đây là **[1, 6]**, trong khi API hai cận nhận **[origin, bound)**. Ta viết:

~~~java
int roll = rng.nextInt(1, 7); // [1, 7) tương đương [1, 6]
~~~

Nhiều lỗi xuất phát từ việc truyền thẳng `max`:

~~~java
int wrong = rng.nextInt(1, 6); // CHỈ 1..5, không thể bằng 6
~~~

Với số nguyên và max không phải giá trị cực đại, quy tắc chuyển là:

~~~text
[minimum, maximum]  =>  [minimum, maximum + 1)
~~~

Nhưng **phép `maximum + 1` phải biểu diễn được trong kiểu dữ liệu**. Nếu `maximum = Integer.MAX_VALUE`, biểu thức `maximum + 1` trong `int` overflow thành `Integer.MIN_VALUE`. Tương tự, `rng.nextInt(Integer.MAX_VALUE + 1)` thành `nextInt(Integer.MIN_VALUE)` và ném lỗi. Dùng `Math.addExact(maximum, 1)` nếu muốn **phát hiện** overflow; hoặc chọn thuật toán mở rộng sang `long` như `nextIntInclusive` trong ví dụ khi thật sự cần bao gồm `Integer.MAX_VALUE`.

Không nên áp dụng công thức đổi miền số nguyên cho `double` một cách mù quáng: “max + 1.0” thêm cả một khoảng thực rộng một đơn vị, không có nghĩa là cho phép chính xác một giá trị max. Miền float/double có tính chất biểu diễn khác miền int/long; hãy xác định rõ hợp đồng có thực sự cần nhận bound hay không, thường chỉ cần [origin, bound) theo API.

### Trường hợp số nguyên sát cực trị

Các lời gọi sau là hợp lệ:

~~~java
int lastBelowMax = rng.nextInt(
        Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
// Giá trị duy nhất: Integer.MAX_VALUE - 1

long maxAsLong = rng.nextLong(
        (long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE + 1L);
// Giá trị duy nhất: 2147483647L
~~~

Tập giá trị của `nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)` bao trùm phần lớn miền int nhưng vẫn **loại trừ chính Integer.MAX_VALUE**. Cần toàn bộ miền `int` có thể dùng `nextInt()` không có cận, hoặc dùng cách xây dựng khoảng đóng đã kiểm soát theo `long` khi có min/max do nghiệp vụ cung cấp.

Với chính kiểu `long`, `Long.MAX_VALUE + 1L` overflow. Không thể dùng mẹo chuyển sang `long` lớn hơn như với int; nếu nghiệp vụ đòi khoảng đóng kết thúc ở `Long.MAX_VALUE`, phải thiết kế riêng bằng cách khác (ví dụ thuật toán chọn mẫu phù hợp với toàn miền hoặc số nguyên lớn hơn), không tự tăng bound trong kiểu long.

## 7. Số thực double: cùng dấu ngoặc khoảng, khác đặc tính biểu diễn

`nextDouble(origin, bound)` tạo giá trị dấu phẩy động trong khoảng **[origin, bound)**. Ví dụ:

~~~java
double fraction = rng.nextDouble(0.0, 1.0);
double temperature = rng.nextDouble(-10.0, 35.0);
~~~

`fraction` có thể bằng 0.0 nhưng không thể bằng 1.0 theo contract; `temperature` có thể sát 35.0 nhưng phải nhỏ hơn 35.0. **Không có bảo đảm** từng điểm trong khoảng thực đều có thể xuất hiện: `double` chỉ biểu diễn tập hữu hạn các số theo IEEE 754; đây không phải lấy ngẫu nhiên trên continuum thực lý tưởng.

Nếu cần chọn ngẫu nhiên số thập phân có bước cố định, chẳng hạn giá tiền cách nhau 0.01, hãy sinh **đơn vị nguyên nhỏ nhất** trong miền đã xác định, rồi chuyển sang biểu diễn thập phân. Đừng lấy `nextDouble`, nhân 100 và làm tròn một cách không phân tích: phân phối trên tập giá trị rời rạc có thể bị lệch ở biên hoặc chịu ảnh hưởng của precision và rounding.

**Vì sao không tự mở rộng bằng `Math.random()` một cách tùy tiện?**

~~~java
double raw = Math.random();            // [0.0, 1.0)
double shifted = -2.0 + raw * 5.0;    // Ý định: [-2.0, 3.0)
~~~

Đây là đoạn mô tả phép biến đổi; các số thực dấu phẩy động được làm tròn khi nhân/cộng. Trong những tình huống biên, phép tính thủ công có thể tạo vấn đề do làm tròn đến bound hoặc hiệu `bound - origin` bị overflow thành Infinity. Khi đã có API `rng.nextDouble(origin, bound)`, ưu tiên dùng nó để diễn đạt trực tiếp chính sách [origin, bound) và kiểm tra hợp lệ của tham số. Bản thân số học dấu phẩy động trong các miền cực đoan vẫn cần kiểm thử.

`Math.random()` trả về giá trị trong [0.0, 1.0); nó không có tham số origin/bound và không phải API phù hợp để sinh token bí mật. Với số thực rất gần nhau, cần hiểu độ phân giải và khả năng biểu diễn; sự khác biệt giữa hai cận phải được diễn giải trên kiểu `double`, không phải trên số thập phân lý tưởng.

## 8. Số lượng giá trị và xác suất ở miền nguyên

Nếu bộ sinh phân phối gần đồng đều trên một khoảng nguyên gồm n giá trị thì xác suất của mỗi giá trị là gần `1/n` (mức độ và bảo đảm chính xác tùy bộ sinh). Với [1, 7), n = 6, mô phỏng xúc xắc cân bằng theo kỳ vọng có mỗi mặt xác suất khoảng 1/6. Đây là lý do cần tránh dùng phép chia dư đơn giản:

~~~java
int wrong = Math.abs(rng.nextInt()) % 6; // Không nên làm thế
int correct = rng.nextInt(6);            // 0..5
~~~

Hai rủi ro ở công thức `wrong`:

1. `Math.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE` vì trị tuyệt đối dương không biểu diễn được trong `int`. Vì vậy kết quả có thể âm.
2. Ép tất cả giá trị ngẫu nhiên vào miền nhỏ bằng phép `%` theo cách không có loại bỏ mẫu (`rejection sampling`) có thể tạo **modulo bias** vì số phần tử trong miền nguồn không chia hết cho độ rộng miền đích.

Hãy dùng bounded API chuẩn để tạo mẫu từ một tập giá trị số nguyên; nó được thiết kế để xử lý giới hạn và giảm thiên lệch phân phối theo hợp đồng generator. Đừng tự viết lại thuật toán sinh số có cận nếu chưa có nhu cầu được chứng minh và kiểm thử thống kê thích hợp.

### Không đánh giá tính đồng đều bằng một vài mẫu

Nếu chạy 10.000 lần và chỉ thấy các giá trị trong [origin, bound), ta mới kiểm chứng **phạm vi của mẫu đã quan sát**. Việc không thấy bound trong một số lượt không chứng minh đầy đủ thuật toán. Tương tự, một bộ mẫu nhỏ có tần suất không bằng nhau không đủ để kết luận generator sai. Khi kiểm thử logic sản phẩm, nên kiểm tra invariant bất biến; khi đánh giá xác suất, cần phương pháp thống kê phù hợp.

## 9. Streams dùng cùng quy tắc khoảng nửa mở

Các API Java như `Random.ints`, `Random.longs`, `Random.doubles` cho phép tạo stream số ngẫu nhiên có giới hạn:

~~~java
import java.util.Random;

Random random = new Random(2026L);
long validCount = random.ints(500, 10, 20)
        .filter(x -> x >= 10 && x < 20)
        .count();

System.out.println(validCount); // 500
~~~

Đây là đoạn nằm trong thân một phương thức (với import tương ứng). Các phần tử đều trong **[10, 20)**, nên số 20 không được sinh. Cần lưu ý `ints(500, 10, 20)` có tham số **streamSize trước, origin và bound sau**; không nhầm với `nextInt(origin, bound)` chỉ có hai cận. `ints(origin, bound)` không có streamSize sinh stream về lý thuyết không giới hạn, nên dùng `limit` hoặc một phép toán kết thúc có điều kiện và kiểm soát tài nguyên.

Stream được tiêu thụ một lần. Với test có seed cố định, tránh giả định các thao tác ở thứ tự khác nhau hoặc generator khác nhau cho ra cùng dãy. Mỗi lời gọi sinh số làm thay đổi trạng thái của generator có trạng thái; không so sánh cứng một mẫu ngẫu nhiên với mẫu được lấy **sau** nó.

## 10. Áp dụng vào các tình huống thực tế

### Chọn chỉ số mảng/list

~~~java
if (!items.isEmpty()) {
    int index = rng.nextInt(items.size()); // [0, size)
    var selected = items.get(index);
}
~~~

Đây là đoạn minh họa đặt trong phương thức có biến `items` là một `List`. `items.size() > 0` là điều kiện cần: list rỗng dẫn tới `nextInt(0)` ném lỗi. Cận trên loại trừ tương thích trực tiếp với vị trí cuối là `size - 1`.

### Sinh số trong khoảng có cả số âm

~~~java
int adjustment = rng.nextInt(-10, 11); // [-10, 10]
~~~

Vì cần bao gồm +10, bound truyền vào là 11. Không nên suy ra `rng.nextInt(-10, 10)` sẽ bao gồm +10.

### Sinh mã số hoặc số phiên ngắn

~~~java
int fourDigits = rng.nextInt(1000, 10_000); // 1000..9999
~~~

Đây chỉ là số giả ngẫu nhiên bốn chữ số, **không** tự bảo đảm duy nhất, không bảo đảm tính khó đoán, không thay thế sequence hoặc cơ chế cấp ID. Nếu cần token phiên đăng nhập, mã xác thực, key hoặc bí mật bảo mật, cần nguồn ngẫu nhiên an toàn mật mã như `SecureRandom` và thiết kế bảo mật phù hợp. Tính đúng của khoảng [origin, bound) chỉ là một phần rất nhỏ của vấn đề đó.

### Lấy số của một enum

~~~java
enum TrafficColor { RED, YELLOW, GREEN }

TrafficColor[] values = TrafficColor.values();
TrafficColor randomColor = values[rng.nextInt(values.length)];
~~~

Các đoạn trong phần 10 là minh họa đặt trong thân class/phương thức phù hợp; enum khai báo ở cấp class, còn thao tác truy cập mảng ở bên trong phương thức. Nếu enum rỗng (không có hằng), phải xử lý riêng trước `nextInt(0)`.

## 11. Kiểm thử thiết kế khoảng đúng hơn

Cần kiểm tra cả **tính đúng của yêu cầu miền** và **hành vi code**:

1. **Ranh giới đơn giản:** [0, 1) chỉ trả 0; [7, 8) chỉ trả 7. Những trường hợp một phần tử giúp bắt nhầm cận rất nhanh.
2. **Origin âm:** [-3, 4) bảo đảm `x >= -3 && x < 4`. Chạy nhiều lần chỉ là kiểm chứng mẫu, không chứng minh thống kê.
3. **Đầu vào lỗi:** `bound <= 0` với overload một cận; `origin >= bound` với overload hai cận; NaN/Infinity với floating bounds.
4. **Cực trị int:** [`Integer.MAX_VALUE - 1`, `Integer.MAX_VALUE`) chỉ có `Integer.MAX_VALUE - 1`. Với khoảng đóng [MAX, MAX], hàm hỗ trợ inclusive trả MAX; công thức cộng `1` kiểu int bị overflow.
5. **Chuyển đổi từ yêu cầu “đến và bao gồm”:** ví dụ xúc xắc [1, 6] phải chuyển thành [1, 7), không phải [1, 6).
6. **Mảng rỗng:** không gọi `nextInt(size)` khi size bằng 0; đặt tiền điều kiện hoặc xử lý nhánh rỗng.
7. **Stream:** kiểm tra đúng thứ tự tham số `(size, origin, bound)`; kích thước stream đầu ra và mọi giá trị thuộc khoảng.
8. **Khả năng tái hiện:** dùng seed cố định trong test cho luồng gọi được kiểm soát, nhưng không đánh đồng seed giống nhau giữa mọi lớp generator hoặc mọi chiến lược phân luồng.
9. **Phân phối và bảo mật:** kiểm thử bất biến không thay thế kiểm định ngẫu nhiên; `Random`/`ThreadLocalRandom` không mặc định là nguồn sinh token an toàn.
10. **Số thực:** kiểm tra hữu hạn và `origin <= x < bound`; không ép kiểm thử phải quan sát cả hai đầu mút vì một đầu mút bị loại, còn đầu mút được phép chưa chắc được lấy trong mẫu hữu hạn.

Một ví dụ nhỏ kiểm tra ngoại lệ theo tiền điều kiện, có thể đặt trong unit test:

~~~java
RandomGenerator rng = new Random(1L);
try {
    rng.nextInt(5, 5);
    throw new AssertionError("Expected IllegalArgumentException");
} catch (IllegalArgumentException expected) {
    // PASS: [5, 5) không có phần tử.
}
~~~

Ở đoạn này cần import `java.util.Random` và `java.util.random.RandomGenerator` và đặt mã trong phương thức. Không dựa vào `assert` của Java khi test chưa bật `-ea`; dùng test framework hoặc kiểm tra luôn thực thi như ví dụ.

## 12. Lỗi phổ biến cần tránh

| Sai lầm | Vì sao sai | Cách làm đúng |
|---|---|---|
| `nextInt(1, 6)` để lấy xúc xắc 1..6 | Số 6 là bound bị loại | `nextInt(1, 7)` |
| `nextInt(0)` để chọn phần tử list rỗng | Không có chỉ số hợp lệ | Xử lý list rỗng trước |
| `nextInt(-5)` để lấy số âm | Overload một cận yêu cầu bound dương | `nextInt(-5, 0)` |
| `nextInt(min, max + 1)` khi max = INT_MAX | Overflow trong phép cộng kiểu int | Dùng `long` có kiểm soát, hoặc thuật toán tổng quát |
| `nextInt(max - min) + min` cho miền lớn | Hiệu `int` có thể overflow | Dùng overload `nextInt(min, max)` |
| Dùng `Math.abs(nextInt()) % n` | Có thể âm, có modulo bias | Dùng `nextInt(n)` |
| Đo `nextDouble` bằng số chữ số thập phân hiển thị | Số thực dấu phẩy động có precision riêng | Kiểm tra bằng bất đẳng thức trên giá trị |
| Tạo token bảo mật bằng `Random` | Generator không dành cho trường hợp đối thủ dự đoán | Dùng `SecureRandom` với quy trình an toàn |
| Dùng vài mẫu để kết luận bound được/không được lấy | Ngẫu nhiên không bảo đảm quan sát mọi khả năng | Dựa vào contract và test biên |
| Viết thuật toán riêng vì muốn cận đóng | Dễ lỗi off-by-one và overflow | Chuyển miền có kiểm soát hoặc helper đã kiểm thử |

## 13. Bài tập và đáp án

**Bài 1.** Viết `nextInt(origin, bound)` cho các yêu cầu: từ 4 đến 9 (bao gồm 9); từ -7 đến -1 (bao gồm -1); từ 0 đến 0 (chỉ một giá trị).

**Bài 2.** Dự đoán kết quả/phạm vi: `nextInt(10, 11)`, `nextInt(-2, 2)`, `nextInt(3, 3)`, `nextInt(10, 9)`.

**Bài 3.** Tại sao `rng.nextInt(1, Integer.MAX_VALUE + 1)` không thể dùng để bao gồm `Integer.MAX_VALUE`? Viết phương án tránh overflow.

**Bài 4.** Vì sao `rng.nextInt(-10, 10)` không tạo giá trị 10, nhưng có thể tạo -10? Hãy phát biểu bất biến kiểm tra mọi kết quả.

**Bài 5.** Một mảng có n = 12 phần tử. Cách chọn ngẫu nhiên một chỉ số hợp lệ và cách xử lý n = 0 là gì?

**Đáp án:**

1. `nextInt(4, 10)`, `nextInt(-7, 0)`, `nextInt(0, 1)`.
2. Chỉ trả 10; từ -2 đến 1; hai lời gọi cuối ném `IllegalArgumentException`.
3. `Integer.MAX_VALUE + 1` overflow kiểu int; có thể dùng helper `nextIntInclusive` của phần 5 với max kiểu int rồi tính upper bằng long.
4. Khoảng là [-10, 10): `value >= -10 && value < 10`.
5. Nếu n > 0, `rng.nextInt(n)` trả chỉ số [0, n); nếu n = 0, xử lý riêng thay vì gọi `nextInt(0)`.

## Tài liệu Java SE 17

- `RandomGenerator` (các overload `nextInt`, `nextLong`, `nextDouble`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/RandomGenerator.html
- `Random`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html
- `ThreadLocalRandom`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadLocalRandom.html
- `SplittableRandom`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SplittableRandom.html
- `Math.random()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html#random()

**Ghi nhớ:** khi đọc tham số `origin` và `bound`, luôn diễn giải theo **`origin <= value < bound`**. Nếu yêu cầu thực tế là [min, max] bao gồm max, chỉ chuyển thành [min, max + 1) sau khi xác minh phép cộng có thể biểu diễn an toàn trong kiểu số đang dùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-008">Sinh số nguyên, số thực và phân phối Gaussian</a>

<details>
<summary>Xem chi tiết</summary>

# Sinh số nguyên, số thực và phân phối Gaussian trong Java

## 1. Mục tiêu và bản chất của số ngẫu nhiên

Trong ứng dụng Java, “sinh số ngẫu nhiên” có thể có nhiều nghĩa khác nhau:

- **Số nguyên phân phối đều (uniform discrete):** mỗi số nguyên trong một tập hữu hạn có xác suất như nhau, ví dụ mô phỏng xúc xắc có sáu mặt.
- **Số thực phân phối đều (uniform continuous, gần đúng bằng số dấu phẩy động):** tạo giá trị trong một khoảng, với xác suất theo độ dài khoảng; ví dụ mô phỏng tọa độ hoặc tỷ lệ.
- **Số thực phân phối chuẩn (Gaussian/normal):** các giá trị tập trung quanh một trung bình, độ phân tán xác định bằng độ lệch chuẩn; ví dụ mô phỏng nhiễu đo lường.
- **Số ngẫu nhiên phục vụ bảo mật:** yêu cầu khó đoán, phù hợp để tạo mã xác minh, salt, khóa hoặc token. Đây là yêu cầu về **độ an toàn của bộ sinh**, khác với chọn phân phối.

Các generator thông dụng tạo **số giả ngẫu nhiên (pseudorandom)**: trạng thái nội bộ cùng thuật toán sinh ra chuỗi có tính chất thống kê mong muốn. Với cùng seed và cùng trình tự gọi, một thuật toán xác định có thể tái tạo chuỗi. “Ngẫu nhiên” không đồng nghĩa “không thể đoán được”, và phân phối đều không đồng nghĩa phân phối Gaussian.

Trong Java 17+, bạn có thể dùng `java.util.Random`, `java.util.concurrent.ThreadLocalRandom`, `java.util.SplittableRandom`, các generator qua `java.util.random.RandomGenerator`, hoặc `java.security.SecureRandom` tùy nhu cầu.

## 2. Sinh số nguyên phân phối đều

### 2.1. Khoảng nửa mở [0, bound)

Với `Random`:

~~~java
import java.util.Random;

Random rng = new Random();
int index = rng.nextInt(10); // 0, 1, 2, ..., 9
~~~

`nextInt(10)` lấy số nguyên **từ 0 (bao gồm) đến 10 (không bao gồm)**, tương đương ký hiệu toán học `[0, 10)`. Khi generator đủ phù hợp cho mô phỏng, các giá trị 0 đến 9 được chọn gần như đều nhau. `nextInt(0)` hoặc `nextInt` với bound âm ném `IllegalArgumentException`.

Với xúc xắc sáu mặt:

~~~java
int dice = rng.nextInt(6) + 1; // 1..6, hai đầu đều bao gồm
~~~

Đây là cách diễn đạt chính xác cho tập {1, 2, 3, 4, 5, 6}. **Không dùng** `rng.nextInt(6)` rồi cho rằng có thể nhận 6; kết quả gốc chỉ đến 5.

### 2.2. Khoảng [origin, bound) và [min, max]

Trong Java 17, giao diện `RandomGenerator` cung cấp các phương thức nhận cả cận đầu và cận cuối, và `Random` thực hiện giao diện này:

~~~java
int level = rng.nextInt(1, 7);   // 1..6
int score = rng.nextInt(20, 81); // 20..80
~~~

Ở đây `origin` bao gồm, `bound` không bao gồm. Yêu cầu `origin < bound`; nếu ngược lại sẽ có `IllegalArgumentException`.

Một nhu cầu thường gặp là muốn cả hai đầu đều bao gồm, ví dụ `min=10`, `max=20`. Có thể dùng `rng.nextInt(min, max + 1)` **khi chắc chắn `max + 1` không tràn `int`**. Với `max == Integer.MAX_VALUE`, phép cộng 1 bị tràn nên công thức đó không an toàn.

Nếu cả hai cận trong một bài toán có thể chạm `Integer.MAX_VALUE`, cân nhắc lấy qua `long`:

~~~java
int min = 10;
int max = Integer.MAX_VALUE;
int sample = (int) rng.nextLong((long) min, (long) max + 1L);
~~~

Đoạn mã trên thực hiện trong Java 17+ vì `Random` kế thừa các phương thức bounded của `RandomGenerator`. Giá trị nằm trong phạm vi `int` đã yêu cầu nên ép kiểu trở lại `int` là hợp lệ. Cần xác thực `min <= max` ở dữ liệu đầu vào. Cách viết này dùng `long` cho phép tính cận trên để tránh tràn khi cộng 1.

### 2.3. Không sinh số nguyên bằng cách làm tròn `nextDouble()`

Các biểu thức sau không có phân phối giống nhau:

~~~java
int good = rng.nextInt(6);                // 0..5, mục tiêu là phân phối đều
int biased = (int) Math.round(rng.nextDouble() * 5); // 0..5, không đều
~~~

Với công thức thứ hai, các giá trị ở hai đầu (0 và 5) nhận những đoạn đầu vào có độ dài xấp xỉ bằng **một nửa** đoạn dành cho các giá trị ở giữa. `Math.round` cũng có quy tắc xử lý đúng nửa riêng, không giải quyết độ lệch xác suất này. Nếu muốn chọn một số nguyên đều nhau, dùng API sinh `int` có cận, thay vì biến đổi số thực bằng `Math.round` hoặc `Math.floor` một cách tùy tiện.

Tương tự, `Math.abs(rng.nextInt()) % n` không phải cách chọn chỉ số tốt: `Math.abs(Integer.MIN_VALUE)` vẫn âm do tràn và phép modulo có thể tạo **modulo bias** khi số đầu vào có số lượng trạng thái không chia hết cho `n`. `nextInt(n)` xử lý phân phối có cận đúng cách cho trường hợp chuẩn.

## 3. Sinh số thực phân phối đều

### 3.1. `nextDouble()` và `nextFloat()`

~~~java
double unit = rng.nextDouble(); // từ 0.0 (gồm) đến 1.0 (không gồm)
float unitFloat = rng.nextFloat(); // cùng dạng khoảng, độ chính xác float
~~~

`nextDouble()` tạo một số `double` giả ngẫu nhiên trong `[0.0, 1.0)`, còn `nextFloat()` tạo `float`. Đây là tập hữu hạn các giá trị có thể biểu diễn bằng dấu phẩy động, **không phải** mọi số thực toán học trong khoảng. Không nên diễn giải đây là độ chính xác tùy ý hoặc số thập phân có số chữ số theo ý muốn.

`Math.random()` là lựa chọn ngắn gọn khi chỉ cần `double` trong `[0.0, 1.0)`:

~~~java
double x = Math.random();
~~~

Tuy nhiên, `Math.random()` không cho truyền seed trực tiếp tại điểm gọi và không phải API dành cho bảo mật. Khi cần tái lập hoặc kiểm soát thuật toán, quản lý generator của mình sẽ rõ ràng hơn.

### 3.2. Khoảng [min, max)

Java 17+ có dạng nhận cận:

~~~java
double temperature = rng.nextDouble(-10.0, 35.0); // [-10.0, 35.0)
float fraction = rng.nextFloat(0.0f, 1.0f);      // [0.0f, 1.0f)
~~~

Với `min < max` hợp lệ, các giá trị được tạo theo phân phối đều gần đúng trên khoảng tương ứng; kết quả nhỏ hơn cận trên. Cách tính cổ điển là:

~~~java
double min = 2.0;
double max = 5.0;
double sample = min + (max - min) * rng.nextDouble();
~~~

Công thức này mô tả ý tưởng `U(min, max)` từ `U(0, 1)`. Tuy nhiên phép nhân và cộng kiểu `double` có thể làm tròn; với một số cận, kết quả trung gian có thể tràn hoặc giá trị sau làm tròn chạm cận trên. Vì vậy, khi có API bounded trực tiếp, nên ưu tiên `nextDouble(min, max)` và truyền đầu vào hữu hạn có cận hợp lệ. Tránh kỳ vọng phép biến đổi dấu phẩy động tự bảo đảm mọi tính chất toán học ở mép khoảng.

### 3.3. Độ chính xác và các chữ số thập phân

`double` không lưu số thập phân một cách chính xác nói chung; ví dụ nhiều số như `0.1` không thể có biểu diễn nhị phân hữu hạn. Đừng nhầm kết quả từ `nextDouble()` với một “số tiền ngẫu nhiên” chính xác đến từng cent.

Nếu muốn chọn một số tiền nằm trong số nguyên các đơn vị nhỏ nhất (như cent), cách đơn giản là **lấy số nguyên có cận trước** rồi quy đổi:

~~~java
int cents = rng.nextInt(100, 501); // 100..500 cent
java.math.BigDecimal amount =
        java.math.BigDecimal.valueOf(cents, 2); // 1.00..5.00
~~~

`BigDecimal.valueOf(cents, 2)` dùng `cents × 10^-2`, giữ chính xác đơn vị cent. Đây là lựa chọn cho mô phỏng giá trị tiền rời rạc; không thay thế `SecureRandom` nếu mục đích là tạo bí mật.

## 4. Phân phối Gaussian (phân phối chuẩn)

### 4.1. Khác gì với phân phối đều?

Với phân phối đều, các phần có cùng độ dài trong khoảng có xác suất xấp xỉ bằng nhau. Với phân phối Gaussian, mật độ xác suất tạo đường cong hình chuông, tập trung quanh trung bình `μ` và thưa dần ở hai phía.

Phân phối chuẩn ký hiệu `X ~ N(μ, σ²)`, trong đó:

- `μ`: trung bình (mean), xác định tâm của phân phối.
- `σ`: độ lệch chuẩn (standard deviation), phải không âm. `σ²` là phương sai (variance). `σ` lớn hơn làm phân phối rộng hơn.
- **Phân phối chuẩn tắc** `Z ~ N(0, 1)` có trung bình 0 và độ lệch chuẩn 1.

Công thức mật độ với `σ > 0`:

\[
f(x)=\frac{1}{\sigma\sqrt{2\pi}}\exp\left(-\frac{(x-\mu)^2}{2\sigma^2}\right).
\]

`f(x)` là **mật độ**, không phải xác suất tại đúng điểm `x`; xác suất trên khoảng được tính bằng diện tích dưới đường cong ở khoảng đó. Các số thực đầu ra của PRNG được lấy rời rạc bằng dấu phẩy động nhưng mô hình xác suất mục tiêu là phân phối liên tục.

Ước lượng quen thuộc cho Gaussian lý tưởng: khoảng 68% nằm trong `μ ± σ`, khoảng 95% trong `μ ± 2σ`, khoảng 99.7% trong `μ ± 3σ`. Đây là tỷ lệ lý thuyết gần đúng, không phải cam kết mỗi tập 100 giá trị tạo ra đều có đúng tỷ lệ ấy.

### 4.2. Sinh Gaussian chuẩn tắc với `nextGaussian()`

~~~java
Random rng = new Random();

double z = rng.nextGaussian(); // N(0, 1), gần đúng theo phân phối chuẩn
~~~

Khác với `nextDouble()`, `nextGaussian()` **không nằm trong [0,1)**. Nó có thể âm, dương, lớn hơn 1 hoặc nhỏ hơn -1. Phân phối chuẩn lý tưởng có hai đuôi kéo dài vô hạn, mặc dù triển khai trên máy tính chỉ trả các giá trị `double` hữu hạn từ tập giá trị máy có thể tạo ra.

Trong `java.util.Random`, `nextGaussian()` dùng biến đổi polar kiểu Box–Muller/Marsaglia từ các giá trị đều và lưu một giá trị Gaussian thứ hai để dùng cho lần gọi tiếp theo. Vì một lần gọi có thể tiêu thụ nhiều giá trị nền do cơ chế thử lại, **không nên suy luận rằng mỗi lần gọi Gaussian luôn chỉ tiêu thụ một lần gọi `nextDouble()`**. Những lần gọi `nextInt`, `nextDouble` hoặc `nextGaussian` xen kẽ cũng làm thay đổi trạng thái và chuỗi kết quả về sau.

### 4.3. Gaussian có trung bình và độ lệch chuẩn tùy chọn

Nếu `Z ~ N(0,1)` thì phép biến đổi:

\[
X = \mu + \sigma Z
\]

cho phân phối `X ~ N(μ, σ²)` khi `σ > 0`. Ví dụ mô phỏng sai số đo quanh 100 với độ lệch chuẩn 15:

~~~java
double mean = 100.0;
double standardDeviation = 15.0;

double measurement = mean + standardDeviation * rng.nextGaussian();
~~~

Java 17+ cũng định nghĩa phương thức trên `RandomGenerator` nhận mean và standard deviation:

~~~java
double measurement2 = rng.nextGaussian(100.0, 15.0);
~~~

Hai cách biểu diễn cùng mô hình phân phối mục tiêu, nhưng **không nên mặc định kết quả bit-for-bit giống nhau với cùng seed** vì các hiện thực có thể sử dụng thuật toán khác nhau hoặc số lượng bước sinh khác nhau. Nếu cần kết quả tái lập chính xác, lưu cả tên thuật toán/kiểu generator, seed và trình tự gọi.

**Lưu ý về tham số:** `15.0` là **độ lệch chuẩn** chứ không phải phương sai. `N(100, 225)` có `σ = 15`. Nhầm phương sai 225 với độ lệch chuẩn làm mô phỏng rộng hơn dự kiến 15 lần.

### 4.4. Gaussian không có giới hạn cứng

Nếu yêu cầu “mô phỏng điểm thi từ 0 đến 100 quanh mức 70”, Gaussian với `μ = 70` và `σ = 15` **vẫn có thể tạo số âm hoặc lớn hơn 100**. Không được giả định Gaussian tự tuân thủ giới hạn chỉ vì phần lớn giá trị gần mean.

Có ít nhất ba hướng xử lý tùy mô hình:

- **Chấp nhận giá trị vượt miền** nếu đại lượng vật lý hoặc phép thử cho phép.
- **Từ chối và sinh lại** giá trị ngoài khoảng; tạo **phân phối chuẩn bị cắt (truncated normal)** trong khoảng, không còn là Gaussian không bị cắt ban đầu. Cần giới hạn số lần thử nếu khoảng rất hẹp hoặc gần đuôi phân phối.
- **Kẹp vào cận** bằng `Math.max(lower, Math.min(upper, sample))`; thao tác này dồn xác suất vào chính hai cận và tạo các khối xác suất ở cận, **không tương đương truncated normal**.

Ví dụ dùng kẹp khi có chủ đích:

~~~java
double raw = 70.0 + 15.0 * rng.nextGaussian();
double bounded = Math.max(0.0, Math.min(100.0, raw));
~~~

Đây là **đoạn mã minh họa trong một phương thức**, không phải chương trình độc lập. Chọn cách xử lý dựa trên ý nghĩa dữ liệu; đừng sửa phân phối âm thầm trong lớp dùng chung.

## 5. Chương trình Java 17+ chạy được

Ví dụ dùng seed cố định để học và kiểm thử. Lưu toàn bộ vào file `RandomDistributionsDemo.java`:

~~~java
import java.util.Locale;
import java.util.Random;

public class RandomDistributionsDemo {
    public static void main(String[] args) {
        Random rng = new Random(12345L);

        System.out.println("int [0,10): " + rng.nextInt(10));
        System.out.println("dice [1,6]: " + (rng.nextInt(6) + 1));

        System.out.printf(Locale.ROOT,
                "double [0,1): %.6f%n", rng.nextDouble());
        System.out.printf(Locale.ROOT,
                "scaled [2,5): %.6f%n",
                2.0 + 3.0 * rng.nextDouble());

        System.out.printf(Locale.ROOT,
                "gaussian Z: %.6f%n", rng.nextGaussian());
        System.out.printf(Locale.ROOT,
                "gaussian N(100,15^2): %.6f%n",
                100.0 + 15.0 * rng.nextGaussian());
    }
}
~~~

Chạy từ terminal:

~~~bash
javac RandomDistributionsDemo.java
java RandomDistributionsDemo
~~~

Output (với `java.util.Random`, seed `12345L` và đúng thứ tự gọi trong chương trình):

~~~text
int [0,10): 1
dice [1,6]: 5
double [0,1): 0.932993
scaled [2,5): 4.499274
gaussian Z: -0.742381
gaussian N(100,15^2): 83.027545
~~~

`Locale.ROOT` giúp dùng dấu chấm thập phân trong `printf`, tránh phụ thuộc locale máy chạy. `%.6f` chỉ định **sáu chữ số sau dấu phẩy khi hiển thị**, không thay đổi dữ liệu ngẫu nhiên bên trong generator. Các giá trị Gaussian không phải “số đẹp” cố định theo phân phối: chúng chính là sáu giá trị cụ thể của một chuỗi giả ngẫu nhiên với seed và thứ tự gọi đã chọn.

Đặc tả `Random` yêu cầu hai đối tượng cùng seed, cùng trình tự method call sinh cùng chuỗi theo thuật toán chuẩn của lớp. Nếu bạn chèn thêm một lần gọi `rng.nextInt(...)` giữa các dòng, những số phía sau có thể thay đổi; điều đó không phải lỗi. Các generator khác có thể cho chuỗi khác dù cùng giá trị seed.

## 6. Sinh nhiều phần tử và kiểm chứng thống kê

### 6.1. Streams cho số nguyên và số thực

`Random` có các phương thức stream tiện dùng khi cần một tập mẫu:

~~~java
Random rng = new Random(42L);

// Tạo đúng 10 số nguyên, mỗi số trong [1, 7).
int[] diceRolls = rng.ints(10, 1, 7).toArray();

// Tạo đúng 5 double, mỗi số trong [2.0, 5.0).
double[] points = rng.doubles(5, 2.0, 5.0).toArray();
~~~

Hai dòng nằm trong ngữ cảnh phương thức với import `java.util.Random`. Nếu yêu cầu rất nhiều phần tử, hãy cân nhắc xử lý dạng stream thay vì luôn tải toàn bộ vào mảng. Khi truyền stream qua pipeline song song, thứ tự tiêu thụ và cách generator được sử dụng có thể ảnh hưởng đến tính tái lập; xem xét generator có hỗ trợ split và tránh chia sẻ mutable state không kiểm soát.

### 6.2. Thử histogram thay vì suy luận bằng vài lần gọi

Bạn có thể tạo nhiều lượt xúc xắc và đếm số lần xuất hiện mỗi mặt:

~~~java
import java.util.Random;

Random rng = new Random(2026L);
int[] counts = new int[6];

for (int i = 0; i < 60_000; i++) {
    int die = rng.nextInt(6) + 1;
    counts[die - 1]++;
}

// counts[0] đếm mặt 1, ..., counts[5] đếm mặt 6.
// Mỗi mặt kỳ vọng khoảng 10_000 lần, không bắt buộc bằng nhau.
~~~

Đây là đoạn mã trong phương thức, không độc lập. Với mô hình đều lý tưởng, kỳ vọng mỗi mặt là `60000/6 = 10000`, nhưng số đếm thực tế dao động. Không nên viết test như `counts[0] == 10000`; ngay cả generator tốt vẫn thường không cho đúng 10000.

Với Gaussian, có thể lấy nhiều mẫu để đo mean và độ lệch chuẩn thực nghiệm; với số lượng mẫu hữu hạn, kết quả sẽ **gần**, chứ không **bằng chính xác**, các tham số lý thuyết. Khi test dữ liệu ngẫu nhiên, nên kiểm tra thuộc tính cận, dạng giá trị, phân phối theo ngưỡng dung sai thống kê có thiết kế, thay vì một vài mẫu cố định rồi khẳng định “đã chứng minh ngẫu nhiên”.

## 7. Chọn generator: Random, ThreadLocalRandom, SplittableRandom, RandomGenerator và SecureRandom

| API | Phù hợp khi | Điểm cần lưu ý |
| --- | --- | --- |
| `Random` | Học tập, ví dụ, test có seed, mã cũ cần tái lập chuỗi | Thread-safe nhưng dùng chung một instance giữa nhiều luồng có thể bị tranh chấp; **không an toàn mật mã**. |
| `ThreadLocalRandom.current()` | Chương trình nhiều luồng cần sinh số trong mỗi luồng | Generator riêng theo luồng, giảm tranh chấp; không chủ động đặt seed bằng `setSeed` theo cách thường dùng; không an toàn mật mã. |
| `SplittableRandom` | Mô phỏng và tác vụ chia nhánh; cần tách generator cho tác vụ độc lập | Không dùng chung instance giữa luồng khi chưa đồng bộ; dùng `split()` để tạo generator độc lập cho nhánh. |
| `RandomGenerator` và `RandomGeneratorFactory` (Java 17+) | Muốn trừu tượng qua giao diện, chọn thuật toán PRNG phù hợp | Có nhiều thuật toán với đặc tính khác nhau; tên thuật toán và cách quản lý seed ảnh hưởng tái lập. |
| `SecureRandom` | Token, salt, nonce, giá trị bảo mật khó đoán | Lấy tính bảo mật từ bộ sinh mật mã/provider; có chi phí và đặc tính tạo seed khác; không nên thay thế bằng `Random` chỉ vì nhanh hơn. |

Ví dụ `ThreadLocalRandom`:

~~~java
import java.util.concurrent.ThreadLocalRandom;

int index = ThreadLocalRandom.current().nextInt(0, 100);
double u = ThreadLocalRandom.current().nextDouble(0.0, 1.0);
double z = ThreadLocalRandom.current().nextGaussian();
~~~

Ví dụ `SplittableRandom` và chia tác vụ:

~~~java
import java.util.SplittableRandom;

SplittableRandom parent = new SplittableRandom(2026L);
SplittableRandom child = parent.split();

int parentRoll = parent.nextInt(1, 7);
int childRoll = child.nextInt(1, 7);
~~~

Ví dụ giao diện `RandomGenerator` trong Java 17:

~~~java
import java.util.random.RandomGenerator;

RandomGenerator generator =
        RandomGenerator.of("L64X128MixRandom");
int integer = generator.nextInt(1, 7);
double real = generator.nextDouble(0.0, 1.0);
double gaussian = generator.nextGaussian();
~~~

Các đoạn này minh họa bên trong phương thức/lớp với import tương ứng; không cam kết output cố định vì một số ví dụ không gắn seed. Việc dùng `RandomGenerator.of` chọn thuật toán theo tên, không tự biến generator thành an toàn mật mã. Java 17 đưa vào nhiều họ thuật toán PRNG mới; khi chọn cho ứng dụng thực, cân nhắc tính chất thống kê, khả năng chia luồng và yêu cầu tái lập.

Với dữ liệu bảo mật, ví dụ:

~~~java
import java.security.SecureRandom;

SecureRandom secure = new SecureRandom();
byte[] tokenBytes = new byte[32];
secure.nextBytes(tokenBytes);
~~~

`32` byte tạo vật liệu ngẫu nhiên cho token; cách mã hóa/lưu trữ/so sánh token phải phù hợp với giao thức. Không dùng `new Random(System.currentTimeMillis())` để tạo bí mật: kẻ tấn công có thể đoán seed hoặc chuỗi dễ hơn. `SecureRandom` cũng có `nextInt(bound)` khi cần chọn giá trị bảo mật trong một miền; tuy nhiên số lượng giá trị khả dĩ trong miền phải đủ lớn để đạt entropy theo yêu cầu. Phân phối Gaussian thường **không thích hợp** để sinh token vì tập giá trị không đồng đều và có thể rò rỉ cấu trúc.

## 8. Seed, tái lập và thread safety

**Seed** là đầu vào khởi tạo trạng thái generator, không phải bằng chứng generator “ngẫu nhiên hơn”. Với hai `new Random(12345L)` và cùng chuỗi lời gọi, kết quả giống nhau. Điều này hữu ích cho unit test, mô phỏng có khả năng tái lập và gỡ lỗi. Nếu thay thứ tự gọi hoặc thay generator, cần kỳ vọng chuỗi thay đổi.

~~~java
Random first = new Random(123L);
Random second = new Random(123L);

System.out.println(first.nextInt(100) == second.nextInt(100)); // true
System.out.println(first.nextDouble() == second.nextDouble()); // true
~~~

Seed cố định trong test là tính năng. Seed có thể đoán trong hệ thống xác thực lại là một rủi ro. Nếu làm việc song song, chia mỗi task một generator có seed được quản lý đúng cách (hoặc một họ generator hỗ trợ split); **không dùng cùng một `Random` vừa để lập báo cáo test hoàn toàn tái lập vừa để phát hành token bí mật**.

`Random` thread-safe, nhưng khi nhiều thread gọi chung, thứ tự xen kẽ có thể không xác định nên việc tái lập theo từng thread khó đạt. `ThreadLocalRandom` giảm tranh chấp nhưng không mang ý nghĩa dễ dàng tái tạo toàn bộ workload bằng một seed người dùng cấp. `SplittableRandom` hỗ trợ tách generator và có ích khi xây dựng mô phỏng song song có cấu trúc rõ ràng.

## 9. Các lỗi thực tế thường gặp

1. **Sai cận trên:** `nextInt(10)` không bao giờ ra `10`; muốn chọn `1..10` thì dùng `nextInt(1, 11)` (Java 17+) hoặc `nextInt(10)+1`.
2. **Giả định cận của số thực là hai đầu đều gồm:** `nextDouble()` nhắm đến `[0,1)`; không diễn giải xác suất lấy đúng 1 là như các điểm nằm bên trong.
3. **Nhầm Gaussian với uniform:** `nextGaussian()` có thể âm hoặc vượt `1`; chỉ `nextDouble()` mới có cận `[0,1)`.
4. **Nhầm phương sai với độ lệch chuẩn:** Trong `N(50, 9)`, `σ = 3`, không phải `9`. Khi viết `mean + sd * nextGaussian()` phải dùng `sd`.
5. **Cho rằng Gaussian tự tuân thủ miền nghiệp vụ:** Nó không có cận cứng; dùng truncate, clamp hoặc lựa chọn mô hình khác một cách có chủ đích.
6. **Dùng `Math.round(rng.nextDouble()*n)` để chia số nguyên đều:** Các điểm biên thường có xác suất khác các điểm giữa; dùng `nextInt(n)`.
7. **Dùng `Math.abs(nextInt()) % bound`:** Vướng `Integer.MIN_VALUE` và nguy cơ modulo bias; dùng phương thức có cận sẵn.
8. **Khởi tạo `new Random()` liên tục trong vòng lặp:** Làm việc với một generator được quản lý hợp lý sẽ rõ trạng thái, rẻ hơn và dễ tái lập hơn. Với ứng dụng đa luồng, cân nhắc generator theo luồng hoặc split.
9. **Giả định seed giống nhau đủ để khẳng định mọi kết quả giống nhau:** Thuật toán và **thứ tự mọi lời gọi** cũng phải tương ứng; `nextGaussian()` có thể tiêu thụ lượng trạng thái khác phép sinh số đều.
10. **Dùng `Random` hoặc `Math.random()` để tạo token bảo mật:** Chúng không phải CSPRNG; dùng `SecureRandom`, đồng thời đánh giá số bit entropy cần thiết.
11. **Kiểm thử xác suất bằng đúng một giá trị cố định:** Một lần sinh không chứng minh phân phối. Dùng seed để xác minh tính tái lập, dùng số lượng mẫu và tiêu chí thống kê phù hợp khi kiểm tra phân phối.
12. **Quên vấn đề dấu phẩy động và overflow cận:** `bound + 1` có thể tràn `int`, và `min + (max-min)*u` có thể phát sinh hiệu ứng làm tròn hoặc tràn. Ưu tiên API bounded khi phù hợp.

## 10. Tự kiểm tra

**Câu 1.** Trong `rng.nextInt(4)`, các giá trị hợp lệ là gì?  
**Đáp án:** `0,1,2,3`. Không có `4`.

**Câu 2.** Cách nào sinh đều số nguyên từ `5` đến `9`, bao gồm hai đầu?  
**Đáp án:** `rng.nextInt(5, 10)` (Java 17+) hoặc `rng.nextInt(5) + 5`.

**Câu 3.** `rng.nextGaussian()` có thể trả `-2.3` không?  
**Đáp án:** Có. Gaussian chuẩn tắc không bị giới hạn trong `[0,1)`.

**Câu 4.** Muốn tạo `X ~ N(80, 25)`, viết thế nào?  
**Đáp án:** `80.0 + 5.0 * rng.nextGaussian()` vì phương sai 25 nghĩa là độ lệch chuẩn 5.

**Câu 5.** Vì sao không nên sinh xúc xắc bằng `Math.round(rng.nextDouble() * 5) + 1`?  
**Đáp án:** Hai mặt đầu và cuối nhận ít độ dài khoảng hơn các mặt ở giữa, làm lệch phân phối.

**Câu 6.** Một ứng dụng cần test có thể chạy lại cùng kết quả, còn một ứng dụng khác cần phát hành token bí mật. Dùng cùng `new Random(12345L)` cho cả hai được không?  
**Đáp án:** Không. Seed cố định hữu ích trong test; token bí mật phải sử dụng bộ sinh an toàn mật mã như `SecureRandom`, với thiết kế token đủ entropy.

**Câu 7.** Kẹp `Gaussian` vào `[0,100]` có tạo đúng phân phối Gaussian bị cắt trong đoạn đó không?  
**Đáp án:** Không. Kẹp đặt mọi mẫu ngoài miền đúng vào 0 hoặc 100, tạo thêm xác suất tại hai điểm biên. Phân phối bị cắt bằng cách từ chối và sinh lại có tính chất khác.

## 11. Tài liệu chính thức

- [`java.util.Random` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html): các phương thức `nextInt`, `nextDouble`, `nextGaussian`, seed và khả năng tái lập.
- [`java.util.random` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/package-summary.html): các họ thuật toán PRNG và lưu ý chọn generator.
- [`ThreadLocalRandom` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadLocalRandom.html): generator riêng theo luồng.
- [`SecureRandom` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html): generator dành cho dữ liệu cần khó dự đoán.

**Ghi nhớ:** Muốn **số nguyên đều**, dùng `nextInt` có cận; muốn **số thực đều**, dùng `nextDouble`/`nextFloat`; muốn **Gaussian**, dùng `nextGaussian` rồi áp dụng `μ + σZ`. Xác định rõ tính chất phân phối, miền giá trị, khả năng tái lập và yêu cầu bảo mật trước khi chọn bộ sinh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-009">Vì sao dùng phép % tùy tiện có thể tạo thiên lệch</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao dùng phép % tùy tiện có thể tạo thiên lệch khi sinh số ngẫu nhiên?

Khi cần tạo một giá trị ngẫu nhiên trong khoảng `0` đến `n - 1`, mã như `randomNumber % n` trông có vẻ hợp lý: phép chia lấy dư đưa một số lớn về một số nhỏ hơn `n`. Nhưng **giới hạn miền giá trị không đồng nghĩa với phân phối đều**. Nếu miền các giá trị ban đầu không chia hết thành các nhóm có cùng số phần tử, một số kết quả sẽ xuất hiện thường xuyên hơn các kết quả còn lại. Đó là **modulo bias** (thiên lệch khi lấy dư).

Ví dụ tạo mặt xúc xắc từ một nguồn giả định sinh đều một trong tám số nguyên `0, 1, ..., 7`:

```java
int face = source % 6; // source nằm trong [0, 8), đồng đều
```

Mã luôn trả một trong `0..5`, nhưng kết quả **không đồng đều**: `0` và `1` có hai tiền ảnh, các số còn lại có một. Nếu mục tiêu là xúc xắc sáu mặt công bằng, không thể dùng trực tiếp phép `% 6` này.

## 1. Chứng minh bằng cách đếm: nguồn có 8 giá trị, đích có 6 giá trị

Giả sử nguồn thật sự đồng đều trên `[0, 8)`, nghĩa là mỗi số nguyên từ 0 đến 7 có xác suất `1/8`. Bảng ánh xạ:

| Giá trị nguồn `x` | `x % 6` | Kết quả cuối |
|---:|---:|---|
| 0 | 0 | 0 |
| 1 | 1 | 1 |
| 2 | 2 | 2 |
| 3 | 3 | 3 |
| 4 | 4 | 4 |
| 5 | 5 | 5 |
| 6 | 0 | 0 (trùng kết quả của 0) |
| 7 | 1 | 1 (trùng kết quả của 1) |

Phân phối sau ánh xạ:

| Kết quả | Số giá trị nguồn tạo ra nó | Xác suất sau `% 6` | Xác suất mong muốn |
|---:|---:|---:|---:|
| 0 | 2 | 2/8 = 25% | 1/6 ≈ 16,67% |
| 1 | 2 | 2/8 = 25% | 1/6 ≈ 16,67% |
| 2 | 1 | 1/8 = 12,5% | 1/6 ≈ 16,67% |
| 3 | 1 | 1/8 = 12,5% | 1/6 ≈ 16,67% |
| 4 | 1 | 1/8 = 12,5% | 1/6 ≈ 16,67% |
| 5 | 1 | 1/8 = 12,5% | 1/6 ≈ 16,67% |

Như vậy kết quả 0 xuất hiện **gấp đôi** kết quả 2 dù các số đầu vào đều có xác suất bằng nhau. Tăng số lần mô phỏng chỉ làm tỷ lệ đo được tiến gần phân phối **đã bị lệch**; chạy nhiều hơn không tự sửa thuật toán.

### Điều kiện tổng quát

Giả sử nguồn đồng đều gồm `N` giá trị nguyên liên tiếp từ `0` đến `N - 1`; cần ánh xạ vào `n` kết quả từ `0` đến `n - 1`. Đặt:

```text
N = q × n + r, trong đó 0 ≤ r < n
q = N / n (chia nguyên)
r = N % n
```

Nếu lấy `x % n` trực tiếp:

- `r` kết quả đầu tiên (`0 .. r - 1`) có `q + 1` giá trị nguồn dẫn đến chúng.
- `n - r` kết quả còn lại (`r .. n - 1`) có `q` giá trị nguồn dẫn đến chúng.
- Khi `r = 0`, mọi kết quả có cùng `q` tiền ảnh: **không có modulo bias do phép ánh xạ này**, với điều kiện nguồn thật sự đồng đều.
- Khi `r ≠ 0`, phân phối bị lệch.

Với `N = 8`, `n = 6`: `q = 1`, `r = 2`. Hai kết quả 0, 1 nhận mỗi kết quả hai tiền ảnh; bốn kết quả còn lại mỗi kết quả một tiền ảnh.

Phép đếm trên là chứng minh trong **miền hữu hạn có phân phối đồng đều**; nó không chứng minh nguồn giả ngẫu nhiên nói chung là chất lượng cao, độc lập hoặc an toàn mật mã.

## 2. Trường hợp quen thuộc: byte ngẫu nhiên rồi `% 10`

Một byte lấy ngẫu nhiên đồng đều có `256` khả năng: `0..255`. Giả sử muốn sinh chữ số thập phân `0..9`:

```java
int digit = unsignedByte % 10; // unsignedByte ở [0, 255]
```

Ta có:

```text
256 = 25 × 10 + 6
```

Vì thế các chữ số `0..5` có **26** giá trị nguồn tương ứng, còn `6..9` chỉ có **25** giá trị nguồn tương ứng:

| Chữ số | Số tiền ảnh | Xác suất |
|---|---:|---:|
| 0, 1, 2, 3, 4, 5 | 26 mỗi chữ số | 26/256 ≈ 10,15625% |
| 6, 7, 8, 9 | 25 mỗi chữ số | 25/256 ≈ 9,765625% |

Phân phối công bằng phải là `10%` cho **mỗi** chữ số. Mức lệch ở đây nhỏ hơn ví dụ 8 → 6, nhưng vẫn là lệch có tính hệ thống. Điều này quan trọng khi sinh nhiều token, chọn phần thưởng, lấy mẫu thống kê hoặc khi yêu cầu bảo mật phải tránh sai lệch biết trước.

**Mức độ thiên lệch phụ thuộc cả kích thước miền nguồn và số lượng kết quả**, không thể kết luận mọi phép `%` đều lệch nhiều hoặc luôn lệch. Chẳng hạn nguồn 256 giá trị lấy `% 16` có đúng 16 tiền ảnh cho mỗi kết quả; còn `% 10` lệch vì 256 không chia hết cho 10.

## 3. Cách sửa bằng rejection sampling (lấy mẫu và loại giá trị dư)

Một cách chuẩn để giữ phân phối đều là **chỉ chấp nhận một miền đầu vào có kích thước chia hết cho `n`**, còn các giá trị ngoài miền thì bốc lại.

Với nguồn 8 giá trị và `n = 6`:

1. Lấy một số đồng đều từ `0..7`.
2. Nếu bằng 6 hoặc 7 thì **loại và lấy lại**.
3. Nếu là `0..5` thì trả chính số đó.

Trong số các lần lấy **được chấp nhận**, mỗi kết quả 0..5 có đúng một tiền ảnh, nên xác suất bằng `1/6`. Mỗi lần thử có xác suất được chấp nhận `6/8 = 75%`, và số lần thử kỳ vọng cho một kết quả hợp lệ là `8/6 = 4/3`.

Mã minh họa thuật toán:

```java
int value;
do {
    value = rng.nextInt(8);
} while (value >= 6);
// value đồng đều trong [0, 6), nếu mỗi lần rng.nextInt(8)
// đồng đều và các lần lấy đáp ứng giả định của nguồn
```

Đây là **đoạn mã trong ngữ cảnh đã khai báo biến `rng`**; với Java thông thường, lời giải đơn giản hơn là **gọi thẳng `rng.nextInt(6)`**.

### Công thức rejection tổng quát cho một byte không dấu

Đối với nguồn nguyên đồng đều `[0, 256)`, nếu cần `n` kết quả và `1 ≤ n ≤ 256`, đặt:

```text
limit = 256 - (256 % n)
```

Giá trị `limit` là bội số lớn nhất của `n` không vượt 256. Lặp:

```java
static int uniformFromByte(java.util.Random rng, int n) {
    if (n < 1 || n > 256) {
        throw new IllegalArgumentException("n must be in [1, 256]");
    }

    int limit = 256 - (256 % n);
    int sample;
    do {
        sample = rng.nextInt(256); // nguồn đồng đều 0..255
    } while (sample >= limit);

    return sample % n; // lúc này mỗi kết quả có số tiền ảnh như nhau
}
```

Đây là **phương thức** cần đặt trong một lớp; nó minh họa toán học chứ không phải cách cần tự triển khai cho hầu hết chương trình Java. Ví dụ `n = 10`: `limit = 250`; loại `250..255`, chấp nhận `0..249`. Mỗi chữ số nhận đúng **25** tiền ảnh, nên xác suất đều bằng `1/10`. Xác suất chấp nhận mỗi lần là `250/256`, số lần thử kỳ vọng `256/250 ≈ 1,024`.

Tại sao ở cuối vẫn dùng `% n`? Vì **sau khi từ chối các mẫu dư, số giá trị được chấp nhận đã chia hết cho `n`**. Phép chia lấy dư khi đó không tạo thiên lệch. Vấn đề nằm ở việc dùng `%` trước khi xử lý miền đầu vào có phần dư, không nằm ở toán tử `%` trong mọi hoàn cảnh.

### Những điều cần kiểm tra khi tự triển khai

- Biết chính xác **miền đầu vào** và giả định phân phối đồng đều; không đoán nguồn dựa vào tên API.
- Miền chấp nhận phải có kích thước là bội số của `n`.
- Phải **lấy mẫu mới** sau khi từ chối, không đổi mẫu bị từ chối thành một kết quả tùy ý.
- Tránh tràn số khi tính bội số, ngưỡng hoặc giá trị tuyệt đối trên miền lớn như 32/64 bit.
- Nếu nguồn không đồng đều sẵn, rejection sampling đơn giản trên tiền ảnh cũng **không tự chữa** mọi khuyết điểm của nguồn.

Với ứng dụng thực tế, nên dùng **API bounded random** của JDK đã xử lý các tình huống này thay vì tự viết thuật toán.

## 4. Java nên dùng API nào?

Khi cần số nguyên ngẫu nhiên **trong một khoảng**, ưu tiên API cung cấp trực tiếp khoảng hoặc cận trên:

```java
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

Random random = new Random();

// Đồng đều trên các giá trị 0..5 theo hợp đồng API:
int zeroBasedDie = random.nextInt(6);

// Mặt xúc xắc 1..6:
int die = random.nextInt(6) + 1;

// Sinh trong khoảng [10, 20) = 10..19:
int value = ThreadLocalRandom.current().nextInt(10, 20);
```

Đoạn mã trên minh họa các phép gọi bên trong một phương thức; nó không phải một chương trình `main` độc lập. Với Java 17 trở lên, giao diện `java.util.random.RandomGenerator` còn định nghĩa các biến thể bounded như `nextInt(bound)` và `nextInt(origin, bound)`.

| Nhu cầu | API điển hình | Khoảng kết quả |
|---|---|---|
| Sinh chỉ số cho `List` có `size > 0` | `random.nextInt(size)` | `0..size-1` |
| Xúc xắc 6 mặt | `random.nextInt(6) + 1` | `1..6` |
| Khoảng nguyên bất kỳ có `origin < bound` | `ThreadLocalRandom.current().nextInt(origin, bound)` | `[origin, bound)` |
| Nhiều tác vụ đồng thời thông thường | `ThreadLocalRandom` hoặc bộ sinh thích hợp với nhu cầu chia sẻ trạng thái | Theo tham số bounded |
| Sinh giá trị khó đoán phục vụ bảo mật | `new SecureRandom().nextInt(bound)` | `0..bound-1` với nguồn ngẫu nhiên mật mã |

**`bound` là cận trên loại trừ**: `nextInt(6)` cho 0 đến 5, không phải 1 đến 6. `nextInt(0)` hoặc cận không hợp lệ không có nghĩa là “lấy số bất kỳ”; thông thường sẽ ném `IllegalArgumentException`.

`Random` và `ThreadLocalRandom` phục vụ sinh giả ngẫu nhiên thông thường, **không được dùng để tạo mã bí mật, token xác thực hoặc giá trị bảo mật khó đoán**. Với trường hợp bảo mật, chọn `SecureRandom`:

```java
import java.security.SecureRandom;

SecureRandom secureRandom = new SecureRandom();
int secureDigit = secureRandom.nextInt(10); // 0..9
```

Ở đây `SecureRandom` giúp bảo vệ tính **khó dự đoán**; việc dùng `nextInt(10)` giúp ánh xạ vào khoảng kết quả theo hợp đồng bounded API. Hai yêu cầu **khó dự đoán** và **phân phối đều** có liên quan nhưng không đồng nhất. Việc lấy một byte ngẫu nhiên mật mã rồi lập tức `% 10` vẫn có modulo bias theo lập luận 256 = 25×10 + 6.

## 5. Ví dụ Java 17+ độc lập, dễ kiểm chứng

Lưu chương trình vào `ModuloBiasDemo.java`. Ví dụ **đếm toàn bộ các trạng thái** thay vì dựa vào mô phỏng may rủi nên cho kết quả xác định ở mỗi lần chạy.

```java
import java.util.Arrays;
import java.util.Random;

public class ModuloBiasDemo {
    private static int fromByteWithRejection(Random random, int bound) {
        if (bound < 1 || bound > 256) {
            throw new IllegalArgumentException("bound must be 1..256");
        }
        int limit = 256 - (256 % bound);
        int value;
        do {
            value = random.nextInt(256);
        } while (value >= limit);
        return value % bound;
    }

    public static void main(String[] args) {
        int[] eightModuloSix = new int[6];
        int[] eightAcceptedSix = new int[6];
        for (int value = 0; value < 8; value++) {
            eightModuloSix[value % 6]++;
            if (value < 6) {
                eightAcceptedSix[value]++;
            }
        }

        System.out.println("8 states % 6: "
                + Arrays.toString(eightModuloSix));
        System.out.println("8 states after rejection: "
                + Arrays.toString(eightAcceptedSix));
        System.out.println("Rejected from 8: " + (8 - 6));

        int[] byteModuloTen = new int[10];
        int[] byteAcceptedTen = new int[10];
        for (int value = 0; value < 256; value++) {
            byteModuloTen[value % 10]++;
            if (value < 250) {
                byteAcceptedTen[value % 10]++;
            }
        }

        System.out.println("256 states % 10: "
                + Arrays.toString(byteModuloTen));
        System.out.println("256 states after rejection: "
                + Arrays.toString(byteAcceptedTen));
        System.out.println("Rejected from 256: " + (256 - 250));

        Random rng = new Random(12345);
        for (int i = 0; i < 10_000; i++) {
            int bounded = rng.nextInt(6);
            int rejection = fromByteWithRejection(rng, 10);
            if (bounded < 0 || bounded >= 6
                    || rejection < 0 || rejection >= 10) {
                throw new AssertionError("Value outside expected range");
            }
        }
        System.out.println("10,000 bounded draws: ranges OK");

        System.out.println("-5 % 6 = " + (-5 % 6));
        System.out.println("floorMod(-5, 6) = " + Math.floorMod(-5, 6));
    }
}
```

Chạy:

```bash
javac ModuloBiasDemo.java
java ModuloBiasDemo
```

Stdout:

```text
8 states % 6: [2, 2, 1, 1, 1, 1]
8 states after rejection: [1, 1, 1, 1, 1, 1]
Rejected from 8: 2
256 states % 10: [26, 26, 26, 26, 26, 26, 25, 25, 25, 25]
256 states after rejection: [25, 25, 25, 25, 25, 25, 25, 25, 25, 25]
Rejected from 256: 6
10,000 bounded draws: ranges OK
-5 % 6 = -5
floorMod(-5, 6) = 1
```

**Cách đọc kết quả:**

1. Hai mảng `8 states` xác nhận bằng phép đếm rằng phép `% 6` trực tiếp cho hai kết quả được ưu ái, còn từ chối `6, 7` tạo số tiền ảnh đồng đều.
2. Hai mảng `256 states` thể hiện chính xác mức lệch của phép `% 10` và cách ngưỡng `250` loại sáu giá trị dư.
3. Vòng lặp `10_000` kiểm tra **giới hạn kết quả** của API và ví dụ rejection, không phải chứng minh bằng thực nghiệm rằng một PRNG bất kỳ có phân phối đều. Chứng minh loại bỏ modulo bias nằm ở phép **đếm miền tiền ảnh**.
4. Dòng `-5 % 6` cho thấy toán tử `%` trong Java là **phép dư mang dấu**, không tự bảo đảm kết quả từ 0 đến 5 khi số đầu vào âm.

## 6. Các bẫy Java liên quan

### Bẫy A: `random.nextInt() % n` có thể âm

`Random.nextInt()` không có đối số trả về số nguyên 32 bit có dấu. Trong Java, phép `%` giữ dấu theo số bị chia:

```java
int remainder = -5 % 6;        // -5
int wrapped = Math.floorMod(-5, 6); // 1
```

`Math.floorMod(x, n)` khi `n > 0` giải quyết bài toán **kết quả âm**, nhưng **không tự loại modulo bias** trên một miền nguồn không chia hết cho `n`. Đừng xem “không âm” là bằng chứng phân phối đều.

### Bẫy B: `Math.abs(random.nextInt()) % n` không phải cách sửa

`Math.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE` vì giá trị tuyệt đối của `-2147483648` không biểu diễn được bằng `int`. Do đó kết quả có thể vẫn âm, ngay cả trước khi `% n`. Ngoài ra, lấy trị tuyệt đối làm gộp các giá trị đối nhau và có thể tạo phân phối không đều; nó không bảo đảm số lần xuất hiện cân bằng.

```java
int value = Math.abs(random.nextInt()) % 6; // tránh
int correct = random.nextInt(6);            // ưu tiên
```

Không nên dùng mẹo `(x % n + n) % n` làm lời giải cho modulo bias: biểu thức đó chỉ chuẩn hóa **phần dư âm** nếu phép tính không gây vấn đề tràn và `n` hợp lệ; nó vẫn không sửa sự khác nhau về số lượng tiền ảnh.

### Bẫy C: Chỉ thử vài nghìn lần rồi kết luận “đều”

Ngay cả một thuật toán không thiên lệch cũng có dao động ngẫu nhiên trong mẫu hữu hạn; một thuật toán có thiên lệch nhỏ cũng có thể trông tương đối đều trong vài lần thử. Để đánh giá cần:

- Lý luận về **miền đầu vào, số lượng tiền ảnh và thuật toán ánh xạ**.
- Kiểm tra hợp đồng API và chất lượng bộ sinh phù hợp mục đích.
- Dùng kiểm định thống kê khi cần đánh giá thực nghiệm, với giả thuyết và kích thước mẫu phù hợp. Kết quả thống kê không thay thế chứng minh về phép ánh xạ.

### Bẫy D: `%` vừa hết không đủ để khẳng định cả bộ sinh tốt

Nếu `N` chia hết cho `n`, riêng bước ánh xạ `x % n` không gây sai lệch tần suất **khi nguồn đồng đều**. Nhưng nếu generator tạo quy luật tuần hoàn dễ đoán, có tương quan hoặc các bit thấp chất lượng kém, việc chỉ lấy phần dư (đặc biệt với lũy thừa của 2) vẫn có thể phơi bày các nhược điểm đó. Do đó hãy dùng API bounded của bộ sinh đáp ứng nhu cầu ứng dụng và đừng suy diễn rằng `%` “an toàn” chỉ vì `n` là lũy thừa của 2.

### Bẫy E: Mỗi lần làm tròn/mapping đều thay đổi phân phối

Các lối tắt khác như `Math.round(Math.random() * 5)` cũng có thể không đều: các điểm ở mép phạm vi nhận độ dài khoảng làm tròn khác điểm ở giữa. Nên tránh suy từ việc “đầu ra nằm trong khoảng” thành “đầu ra đồng đều”. Nếu dùng số nguyên bounded, hãy gọi API nguyên trực tiếp.

## 7. Trường hợp thực tế và lựa chọn phù hợp

| Tình huống | Cách tiếp cận |
|---|---|
| Chọn ngẫu nhiên một phần tử trong danh sách có `size > 0` | `list.get(rng.nextInt(list.size()))` |
| Mô phỏng xúc xắc công bằng 1 đến 6 | `rng.nextInt(6) + 1` |
| Tạo số nguyên trong `[min, max)` | API `nextInt(min, max)`; chú ý cận trên loại trừ và miền hợp lệ |
| Tạo mã xác thực/token bảo mật bằng bảng ký tự | Dùng `SecureRandom` và API bounded; quản lý thêm độ dài, entropy và chính sách token |
| Có sẵn byte ngẫu nhiên đồng đều, cần chữ số 0..9 | Loại `250..255` rồi lấy `% 10`, hoặc dùng trực tiếp API sinh số trong `[0, 10)` |
| Dữ liệu mẫu thiên lệch theo chủ đích | Xác định và triển khai phân phối mong muốn; không vô tình tạo thiên lệch bằng phép dư |

Hai yêu cầu cần tách biệt:

- **Đồng đều:** các kết quả trong miền đích có cùng xác suất.
- **Khó đoán:** người quan sát không thể suy ra kết quả tiếp theo với khả năng thực tế đáng kể.

`Random.nextInt(bound)` có thể phù hợp mô phỏng thông thường nhưng không cung cấp cam kết bảo mật của `SecureRandom`. Ngược lại, nguồn khó đoán vẫn có thể bị ánh xạ sai thành phân phối có thiên lệch nếu dùng `%` một cách tùy tiện.

## 8. Tự kiểm tra

**Câu 1.** Nguồn đồng đều `0..9`, dùng `x % 4`: kết quả nào xuất hiện nhiều hơn?  
**Đáp án:** `10 = 2×4 + 2`; kết quả `0` và `1` có ba tiền ảnh mỗi số, `2` và `3` có hai tiền ảnh mỗi số. Xác suất lần lượt 30%, 30%, 20%, 20%; phân phối không đều.

**Câu 2.** Nguồn đồng đều `0..15`, dùng `x % 4` có modulo bias không?  
**Đáp án:** Không có bias do ánh xạ này vì `16` chia hết `4`. Mỗi kết quả có đúng bốn tiền ảnh. Điều này không tự nói lên chất lượng hoặc an toàn của nguồn.

**Câu 3.** Tại sao loại `250..255` trước khi lấy `% 10` trên byte ngẫu nhiên đều lại hợp lý?  
**Đáp án:** Miền chấp nhận `0..249` có 250 giá trị, chia hết cho 10; mỗi chữ số 0..9 có 25 tiền ảnh. Mẫu bị loại phải được **lấy mới**.

**Câu 4.** `Math.floorMod(random.nextInt(), 6)` đã loại được modulo bias chưa?  
**Đáp án:** Chưa. Hàm giải quyết phần dư âm; miền nguồn nguyên 32 bit có `2^32` giá trị, không chia hết cho 6, nên vẫn tồn tại độ lệch tần suất do ánh xạ.

**Câu 5.** Vì sao `Math.abs(random.nextInt())` không bảo đảm không âm?  
**Đáp án:** `Integer.MIN_VALUE` không có trị tuyệt đối biểu diễn được bằng kiểu `int`, nên `Math.abs(Integer.MIN_VALUE)` vẫn âm.

**Câu 6.** Với Java, nên viết gì để chọn đều một chỉ số từ danh sách có 10 phần tử?  
**Đáp án:** `rng.nextInt(10)`. Nếu lựa chọn liên quan bí mật hoặc bảo mật, dùng bộ sinh phù hợp như `SecureRandom.nextInt(10)`.

**Ghi nhớ:** `x % n` đưa giá trị về một số dư nhưng **không tự bảo đảm phân phối đều**. Nếu số trạng thái nguồn không chia hết cho `n`, phần dư có nhiều tiền ảnh hơn sẽ được ưu ái. Dùng **bounded RNG API** của Java hoặc **rejection sampling** đúng cách để tránh thiên lệch này.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-010">SecureRandom cho yêu cầu không thể dự đoán</a>

<details>
<summary>Xem chi tiết</summary>

# `SecureRandom` cho yêu cầu không thể dự đoán trong Java

## 1. Vì sao “ngẫu nhiên” chưa đủ cho bảo mật?

Một số tác vụ chỉ cần dữ liệu **trông có vẻ ngẫu nhiên**: xáo trộn câu hỏi luyện tập, giả lập tung xúc xắc, sinh dữ liệu kiểm thử. Nhưng một số tác vụ đòi hỏi kẻ tấn công **không thể dự đoán khả thi** giá trị kế tiếp, kể cả khi họ đã nhìn thấy nhiều giá trị trước đó. Đó là yêu cầu phổ biến khi sinh:

- Token đặt lại mật khẩu hoặc xác minh email chứa bí mật.
- Session token, API token, nonce bí mật, mã truy cập ngẫu nhiên.
- OTP được sinh bởi máy chủ (kèm kiểm soát số lần thử).
- Vật liệu ngẫu nhiên dành cho một số thao tác mật mã.

Trong Java, `java.security.SecureRandom` là API sinh số ngẫu nhiên dành cho bối cảnh bảo mật. Nó lấy đầu ra từ một bộ sinh được cung cấp qua **security provider**; với triển khai mật mã phù hợp và nguồn seed đủ entropy, đầu ra được thiết kế để chống dự đoán bằng phương pháp tính toán.

`java.util.Random`, `Math.random()` và `ThreadLocalRandom` có ích cho mô phỏng hay ứng dụng thông thường, nhưng **không đáp ứng hợp đồng cần tính không thể dự đoán của bí mật bảo mật**. Một bộ sinh có thống kê phân bố “đẹp” vẫn có thể bị dự đoán nếu biết trạng thái hoặc seed. Ngược lại, `SecureRandom` ưu tiên thuộc tính bảo mật; không nên lấy một vài kết quả trông hỗn loạn để chứng minh thuật toán an toàn.

Sau bài học, bạn có thể chọn đúng API, tạo token dạng byte và mã OTP bằng `SecureRandom`, hiểu nguồn seed/provider, tránh modulo bias, và kết hợp token với các kiểm soát vận hành phù hợp.

## 2. Mô hình tư duy: random thông thường và cryptographic random

| Tiêu chí | `Random`, `ThreadLocalRandom`, `Math.random()` | `SecureRandom` |
|---|---|---|
| Mục tiêu chính | Mô phỏng, phân bố thống kê, tốc độ | Giá trị khó dự đoán về mặt tính toán |
| Dùng cho token bí mật | **Không nên** | **Nên**, với độ dài và quản lý token phù hợp |
| Seed cố định để tái hiện test | Thường hữu ích với `new Random(seed)` | Không phải cách phù hợp để tạo bí mật thật |
| Thuộc tính đầu ra | Phụ thuộc thuật toán, không cam kết an toàn mật mã | Theo thuật toán/provider mật mã đã chọn |
| Hiệu năng | Thường phù hợp cho lượng lớn dữ liệu mô phỏng | Có thể tốn chi phí khởi tạo hoặc lấy entropy hơn |
| Kết quả có luôn duy nhất? | Không | **Cũng không**; ngẫu nhiên khác với đảm bảo duy nhất |

**Không thể dự đoán** không đồng nghĩa **không bao giờ trùng**. Ngay cả token dài 256 bit cũng có xác suất va chạm khác 0; nếu hệ thống bắt buộc khóa duy nhất, cần ràng buộc dữ liệu tương ứng và xử lý va chạm hiếm gặp.

Về mặt triển khai, nhiều thuật toán `SecureRandom` là **CSPRNG** (*cryptographically secure pseudorandom number generator*): trạng thái bên trong và seed hỗ trợ tạo ra chuỗi đầu ra có đặc tính chống dự đoán. Điều này không có nghĩa từng byte phải được đọc trực tiếp từ phần cứng ở mỗi lần gọi. Một số provider còn có cơ chế RNG khác; hợp đồng `SecureRandom` trừu tượng hóa lựa chọn này.

### Khi nào phải dùng `SecureRandom`?

Đặt câu hỏi: “Nếu người ngoài đoán được giá trị tiếp theo, họ có thể chiếm tài khoản, bỏ qua xác minh, giả mạo yêu cầu hay tiếp cận tài nguyên không?” Nếu câu trả lời là **có**, đây là trường hợp điển hình cần `SecureRandom` (và các biện pháp bảo vệ khác), thay vì generator phục vụ mô phỏng.

## 3. API quan trọng và cách khởi tạo

Bắt đầu bằng cách đơn giản, không tự cấu hình seed:

```java
import java.security.SecureRandom;

SecureRandom secureRandom = new SecureRandom();
```

Đây là đoạn mã minh họa đặt trong lớp hoặc phương thức. Constructor không tham số chọn một thuật toán và provider mặc định theo cấu hình Java đang chạy. Đừng giả định tất cả máy hoặc phiên bản JDK đều chọn cùng thuật toán.

Một số phương thức cần nắm:

| API | Ý nghĩa |
|---|---|
| `new SecureRandom()` | Lấy bộ sinh mặc định do Java/provider chọn |
| `nextBytes(byte[] bytes)` | Điền byte ngẫu nhiên vào mảng đã cấp |
| `nextInt(int bound)` | Số `int` phân bố đều trong `[0, bound)`; `bound > 0` |
| `nextLong()` | Lấy giá trị `long` ngẫu nhiên |
| `generateSeed(int numBytes)` | Tạo các byte dành cho **seed**, chủ yếu cho bộ sinh khác |
| `getAlgorithm()`, `getProvider()` | Cho biết thuật toán/provider thực tế, hữu ích khi chẩn đoán |
| `SecureRandom.getInstance("DRBG")` | Yêu cầu thuật toán cụ thể; có thể ném `NoSuchAlgorithmException` nếu môi trường không hỗ trợ |
| `SecureRandom.getInstanceStrong()` | Chọn theo cấu hình `securerandom.strongAlgorithms`; có thể có chi phí/chờ entropy tùy provider |

**Lựa chọn mặc định:** Đối với token thông thường trong ứng dụng Java hiện đại, `new SecureRandom()` cùng `nextBytes()` thường là điểm bắt đầu hợp lý. Nếu tổ chức có yêu cầu cụ thể về thuật toán, compliance, DRBG, FIPS hoặc provider, hãy cấu hình và kiểm chứng riêng theo môi trường triển khai. Tên `getInstanceStrong()` không có nghĩa cứ đổi sang đó là hệ thống an toàn hơn trong mọi trường hợp; thuật toán được chọn, khả năng chờ và hiệu năng phụ thuộc cấu hình.

## 4. Ví dụ hoàn chỉnh: token bí mật 32 byte an toàn cho URL

**Mục tiêu:** Sinh **32 byte ngẫu nhiên** (256 bit đầu ra), rồi mã hóa sang Base64 URL-safe **không padding**. Dạng này có thể dùng làm bí mật trong đường dẫn hoặc tham số truyền nếu được xử lý cẩn thận. Base64 chỉ **mã hóa biểu diễn**, không mã hóa bảo mật; giá trị token vẫn là bí mật phải bảo vệ.

**Tệp `SecureTokenDemo.java` — Java 17+:**

```java
import java.security.SecureRandom;
import java.util.Base64;

public class SecureTokenDemo {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public static void main(String[] args) {
        String token = newToken();

        // Chỉ in thuộc tính kiểm thử, không in bí mật thật.
        System.out.println("tokenLength=" + token.length());
        System.out.println("decodedBytes="
                + Base64.getUrlDecoder().decode(token).length);
        System.out.println("urlSafe="
                + token.matches("[A-Za-z0-9_-]{43}"));
    }
}
```

Biên dịch và chạy:

```bash
javac SecureTokenDemo.java
java SecureTokenDemo
```

Kết quả:

```text
tokenLength=43
decodedBytes=32
urlSafe=true
```

Kết quả in ra ổn định vì chương trình kiểm tra **hình dạng**, không in nội dung token ngẫu nhiên. Giá trị token thay đổi giữa các lần chạy, nhưng **không cam kết mọi lần đều khác nhau tuyệt đối**.

Giải thích từng bước:

1. `new byte[32]` cấp đúng 32 byte.
2. `RANDOM.nextBytes(bytes)` điền các byte không thể dự đoán khả thi theo cam kết của provider bảo mật.
3. `Base64.getUrlEncoder()` dùng bảng ký tự `A–Z`, `a–z`, `0–9`, `-`, `_`, phù hợp để đưa vào URL hơn Base64 thông thường dùng `+` và `/`.
4. `withoutPadding()` bỏ dấu `=` cuối; **32 byte cho chuỗi Base64URL không padding dài 43 ký tự**.
5. Khi cần khôi phục byte ban đầu, `Base64.getUrlDecoder().decode(token)` giải mã lại đúng 32 byte.

Nếu mỗi byte có entropy lý tưởng, 32 byte tương ứng không gian khoảng `2^256` giá trị. Tuy nhiên, **độ dài 256 bit không tự động bảo đảm toàn bộ hệ thống an toàn 256 bit**: phải có nguồn entropy/provider tốt, không rò rỉ token và xử lý vòng đời bí mật đúng cách. Việc cắt chuỗi kết quả xuống vài ký tự sẽ làm giảm đáng kể không gian đoán.

### Vì sao không in token ra console?

Token reset hoặc session là **bearer secret**: bất kỳ ai sở hữu token hợp lệ trong thời gian có hiệu lực đều có thể sử dụng quyền mà token đại diện. Log, telemetry, lịch sử URL, hệ thống theo dõi lỗi và analytics đều có thể vô tình giữ lại giá trị bí mật. Ví dụ trên cố ý chỉ in các thuộc tính kiểm tra, không in token.

## 5. Tạo mã OTP sáu chữ số: ngẫu nhiên mạnh vẫn cần bảo vệ bổ sung

OTP dạng `000000`–`999999` chỉ có **1.000.000 giá trị khả dĩ**. `SecureRandom` giúp kẻ tấn công khó dự đoán chuỗi phát sinh, nhưng **không làm tăng không gian đoán** của một mã sáu chữ số. Mã ngắn bắt buộc phải đi kèm TTL ngắn, hạn chế lượt thử, xác minh một lần và ràng buộc vào phiên/yêu cầu/người nhận.

**Tệp `SecureOtpDemo.java`:**

```java
import java.security.SecureRandom;
import java.util.Locale;

public class SecureOtpDemo {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String sixDigitCode() {
        int value = RANDOM.nextInt(1_000_000);
        return String.format(Locale.ROOT, "%06d", value);
    }

    public static void main(String[] args) {
        String otp = sixDigitCode();

        System.out.println("otpLength=" + otp.length());
        System.out.println("onlyDigits=" + otp.matches("[0-9]{6}"));
    }
}
```

```bash
javac SecureOtpDemo.java
java SecureOtpDemo
```

Kết quả:

```text
otpLength=6
onlyDigits=true
```

Các chi tiết dễ bị bỏ qua:

- `nextInt(1_000_000)` trả số từ **0 đến 999999**, không gồm 1000000; giới hạn phải dương.
- `%06d` giữ **đủ 6 chữ số**, kể cả trường hợp giá trị ngẫu nhiên là `000042`.
- Với một OTP cho phép bắt đầu bằng `0`, không dùng `100_000 + RANDOM.nextInt(900_000)`: cách đó bỏ mất toàn bộ các mã bắt đầu bằng 0 và chỉ còn 900.000 khả năng.
- Nếu chính sách **cấm số 0 đứng đầu**, lúc đó mới dùng miền `100000`–`999999`; đây là quy tắc nghiệp vụ khác.
- **Không** sinh số bằng `Math.abs(RANDOM.nextInt()) % 1_000_000`: modulo không bảo đảm phân bố đều, `Math.abs(Integer.MIN_VALUE)` vẫn âm do tràn, và việc lấy phần dư không xử lý được điều kiện bảo mật.

Đối với miền có giới hạn, dùng trực tiếp `nextInt(bound)` để API chọn giá trị phân bố đều mà không tự viết phép chia dư có bias. Ví dụ chọn chỉ số trong danh sách 10 phần tử: `RANDOM.nextInt(10)` trả các giá trị `0`–`9`.

### OTP được bảo vệ như thế nào ở hệ thống thật?

Cần kết hợp các ràng buộc: mã có hiệu lực ngắn; giới hạn số lượt thử theo tài khoản/yêu cầu/IP phù hợp; khóa hoặc vô hiệu hóa sau quá nhiều lần thử; buộc mã gắn với đúng thao tác, người nhận và thời điểm; mỗi mã chỉ được dùng thành công một lần. Nếu có nhiều request đồng thời, trạng thái “đã dùng” phải được cập nhật **nguyên tử** để ngăn sử dụng lặp. Tất cả kiểm tra cần thực hiện ở máy chủ.

## 6. Vòng đời của token: sinh tốt mới chỉ là bước đầu

Một luồng token đặt lại mật khẩu điển hình:

```text
Máy chủ
  |
  | SecureRandom.nextBytes(32)
  v
Token bí mật (Base64URL) ------> Gửi riêng cho chủ tài khoản
  |
  | SHA-256(token) / hash mật mã thích hợp
  v
Lưu hash + accountId + purpose + expiresAt + used=false
  |
  | Khi người dùng gửi token quay lại
  v
Hash token được gửi -> so khớp với bản lưu
  |
  +-- sai / hết hạn / sai mục đích / đã dùng: từ chối
  |
  +-- đúng: thực hiện tác vụ và đánh dấu used=true theo giao dịch an toàn
```

Trong ứng dụng thật, lưu **hash** của token ngẫu nhiên dài (thay vì giữ token gốc ở dạng plaintext trong cơ sở dữ liệu) có thể hạn chế tác động khi bảng token bị lộ. Với token có entropy cao, hàm băm SHA-256 thường phù hợp để làm dấu đối chiếu; mã OTP sáu chữ số có entropy thấp thì **không thể dựa vào SHA-256 đơn thuần để chống dò offline nếu bị lộ bản hash**. OTP đòi hỏi mô hình lưu trữ và kiểm soát riêng, có thể dùng HMAC với khóa bí mật của máy chủ cùng giới hạn lượt thử và thời hạn.

Đoạn code độc lập sau minh họa cách chỉ lưu/so sánh digest cho token entropy cao, **chưa thay thế đầy đủ** tầng xác thực, TTL hay cập nhật “used” nguyên tử.

**Tệp `TokenDigestDemo.java`:**

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenDigestDemo {
    private static byte[] sha256(String text)
            throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.US_ASCII));
    }

    public static void main(String[] args)
            throws NoSuchAlgorithmException {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        byte[] storedDigest = sha256(token);
        byte[] presentedDigest = sha256(token);

        System.out.println("digestBytes=" + storedDigest.length);
        System.out.println("match="
                + MessageDigest.isEqual(storedDigest, presentedDigest));
    }
}
```

```bash
javac TokenDigestDemo.java
java TokenDigestDemo
```

```text
digestBytes=32
match=true
```

`SHA-256` tạo digest 32 byte; `MessageDigest.isEqual` là API so sánh mảng byte thích hợp cho giá trị nhạy cảm về thời gian so sánh. Ở ví dụ này, `token` là giá trị riêng tư trong chương trình nhưng không được in ra; trong ứng dụng thật, máy chủ lưu `storedDigest` và chỉ chuyển token gốc đến người dùng qua kênh được bảo vệ.

**Không nhầm SHA-256 với thuật toán hash mật khẩu người dùng.** Mật khẩu do người dùng chọn có thể bị đoán, nên thường cần thuật toán KDF chuyên dụng như Argon2id, scrypt hoặc PBKDF2 với tham số phù hợp. Token ngẫu nhiên dài và OTP ngắn là hai tình huống lưu trữ có mô hình rủi ro khác nhau.

## 7. Seed, entropy, provider và `getInstanceStrong()`

### 7.1. Seed quyết định khả năng chống dự đoán

CSPRNG cần trạng thái khởi tạo có entropy đủ mạnh. Một `SecureRandom` mặc định thường tự khởi tạo bằng cơ chế/provider thích hợp khi cần dùng; lập trình viên không phải tự tạo seed từ đồng hồ.

**Tránh:**

```java
// Minh họa cách không nên dùng để tạo token bảo mật.
byte[] guessableSeed = {
        1, 2, 3, 4
};
SecureRandom random = new SecureRandom(guessableSeed);
```

Seed cố định hoặc đoán được từ thời gian, PID, tên tài khoản, mã học viên... có thể làm giảm thuộc tính không thể dự đoán. Cũng không có cơ sở để cho rằng chỉ vì tham số kiểu `byte[]` dài thì seed đã có entropy cao. Tùy provider, việc cung cấp seed và hành vi reseed khác nhau; **không dùng seed tự chế để tạo hoặc kiểm thử token thật**.

Một điểm API: `setSeed(...)` trên `SecureRandom` có thể bổ sung entropy vào trạng thái hiện có, chứ không nhất thiết thay thế seed như một cơ chế khởi tạo `Random` thông thường. Không nên dựa vào seed cố định để tái tạo cùng chuỗi `SecureRandom` giữa các JDK, provider hay máy chạy. Trong kiểm thử logic phụ thuộc nguồn ngẫu nhiên, có thể **tiêm giao diện nguồn dữ liệu giả** (fake) cho test, còn môi trường production phải dùng CSPRNG thật.

### 7.2. `getInstanceStrong()` có luôn là lựa chọn tốt hơn?

`SecureRandom.getInstanceStrong()` đọc cấu hình các thuật toán được ưu tiên mạnh tại thuộc tính bảo mật `securerandom.strongAlgorithms`. Phương thức có thể ném `NoSuchAlgorithmException`, và một số nguồn entropy/provider có thể chặn trong khi khởi tạo hoặc sinh dữ liệu.

Đối với endpoint chịu nhiều truy cập và cần token ngắn hạn, không nên tự động thay mọi lệnh `new SecureRandom()` bằng `getInstanceStrong()` mà chưa đánh giá thuật toán, cấu hình triển khai, tốc độ và độ trễ. Nếu yêu cầu tổ chức bắt buộc dùng DRBG hoặc provider cụ thể, hãy chọn tường minh theo quy định và kiểm thử trên đúng runtime, bao gồm trường hợp thiếu provider.

### 7.3. `nextBytes` và `generateSeed` khác nhau

Để **sinh token sử dụng ngay**, phương thức tiêu chuẩn là `nextBytes(byte[])`. `generateSeed(int)` có mục đích tạo byte seed, chẳng hạn để khởi tạo một RNG khác; nó có đặc điểm lấy entropy và hiệu năng phụ thuộc provider. Không bắt buộc phải gọi `generateSeed` trước `nextBytes` khi dùng `new SecureRandom()`.

### 7.4. Dùng cùng một đối tượng `SecureRandom`

Trong Java hiện đại, `SecureRandom` được tài liệu hóa là an toàn khi nhiều thread sử dụng chung; lớp có thể đồng bộ hoạt động với provider không công bố hỗ trợ thread safety. Vì vậy, cách đặt `private static final SecureRandom RANDOM = new SecureRandom();` trong ví dụ là một mô hình hợp lệ cho nhiều ứng dụng.

Nếu throughput rất cao, vẫn nên đo đạc trên provider thực tế: mức contention, entropy và khởi tạo có thể ảnh hưởng hiệu năng. Không tạo lại `SecureRandom` bên trong mỗi vòng lặp chỉ để hy vọng kết quả “ngẫu nhiên hơn”.

## 8. Các lỗi thường gặp

| Lỗi | Tác hại | Cách làm phù hợp |
|---|---|---|
| Dùng `Math.random()` cho token reset mật khẩu | Không có hợp đồng chống dự đoán của CSPRNG | Dùng `SecureRandom.nextBytes` |
| Dùng `new Random(System.currentTimeMillis())` cho OTP | Seed đồng hồ có thể đoán | Dùng `SecureRandom.nextInt(bound)` |
| Tạo token rất ngắn rồi tin rằng `SecureRandom` là đủ | Không gian đoán thấp | Dùng đủ số byte và bảo vệ vòng đời |
| Dùng `Math.abs(nextInt()) % bound` | Có thể bias và gặp `Integer.MIN_VALUE` | `nextInt(bound)` |
| Dùng Base64 rồi cho rằng đã mã hóa bí mật | Base64 dễ dàng giải mã | Coi chuỗi Base64URL là bearer secret |
| Ghi token thô vào log hoặc analytics | Rò rỉ bí mật | Chỉ ghi metadata không nhạy cảm |
| Dùng mã OTP không TTL/không giới hạn thử | Có thể brute force online | TTL, rate limit, khóa/thay mã và một lần dùng |
| Mặc định `getInstanceStrong()` luôn nhanh và không chặn | Có thể tăng độ trễ/khác provider | Đánh giá môi trường triển khai |
| Tạo seed từ timestamp hoặc một chuỗi hằng | Seed kém entropy | Để provider cung cấp entropy thích hợp |
| Cho rằng token ngẫu nhiên luôn duy nhất | Va chạm tuy hiếm vẫn có thể xảy ra | Kiểm tra/ràng buộc uniqueness nếu cần |
| In thử vài token rồi kết luận an toàn | Đầu ra “không lặp” không chứng minh chống dự đoán | Dựa vào thuật toán/provider phù hợp và kiểm thử hệ thống |

## 9. Cách kiểm thử đúng khi kết quả phải thay đổi

Vì đầu ra của CSPRNG không có giá trị cố định, không viết test dạng:

```java
// KHÔNG đúng: không thể kỳ vọng một giá trị token cố định.
assert token.equals("abc123");
```

Thay vào đó, kiểm thử những thuộc tính tất định:

```java
String token = SecureTokenDemo.newToken();

if (token.length() != 43
        || !token.matches("[A-Za-z0-9_-]{43}")
        || Base64.getUrlDecoder().decode(token).length != 32) {
    throw new AssertionError("Invalid token format");
}
```

Đây là đoạn mã minh họa dùng cùng lớp `SecureTokenDemo` đã định nghĩa ở mục 4; cần import `java.util.Base64` khi đặt trong tệp riêng. Có thể kiểm tra bổ sung việc token bị sửa hoặc đã dùng sẽ bị từ chối và cách xử lý sau khi hết hạn.

Các **bài kiểm tra phân bố thống kê** không chứng minh tính không thể dự đoán của một RNG. Tránh viết test dễ flake kiểu “100 token chắc chắn khác nhau” rồi xem đó là bằng chứng mật mã. Khi cần bảo đảm thuật toán tuân thủ tiêu chuẩn hoặc cấu hình provider, hãy dựa vào thư viện/provider được đánh giá, môi trường runtime được quản lý và test tích hợp đối với đường xử lý bí mật.

## 10. Câu hỏi tự kiểm tra

1. **Ứng dụng xáo trộn câu hỏi trắc nghiệm, kết quả không dùng làm bí mật. Có bắt buộc `SecureRandom` không?**  
   Không. `Random` hoặc một nguồn ngẫu nhiên thông thường có thể đủ nếu không có yêu cầu bảo mật.

2. **Ứng dụng phát token reset mật khẩu, người ngoài thấy token của vài người dùng khác. Nên dùng gì?**  
   Một CSPRNG như `SecureRandom`, tạo đủ byte entropy và bảo vệ token khi truyền, lưu, dùng và hết hạn.

3. **`RANDOM.nextInt(6)` có trả số 6 không?**  
   Không. Nó trả một trong `0, 1, 2, 3, 4, 5`.

4. **Vì sao `%06d` quan trọng trong OTP sáu chữ số?**  
   Vì số ngẫu nhiên `42` phải được biểu diễn thành `000042` nếu miền cho phép số 0 đứng đầu.

5. **Base64URL biến token thành dữ liệu đã mã hóa bảo mật hay chưa?**  
   Chưa. Đây chỉ là cách biểu diễn byte thành chuỗi có thể đọc/truyền; người nhận có thể giải mã mà không cần khóa.

6. **Vì sao OTP `000000`–`999999` vẫn có thể bị brute force dù sinh bằng `SecureRandom`?**  
   Vì chỉ có 1.000.000 khả năng; cần TTL, giới hạn lượt thử, ràng buộc phiên và cơ chế sử dụng một lần.

7. **Có nên dùng `SecureRandom.getInstanceStrong()` cho mọi request mà không kiểm tra provider?**  
   Không nên. Việc chọn thuật toán và chi phí/lúc chờ entropy phụ thuộc cấu hình, cần được đánh giá theo runtime.

8. **`SecureRandom` có đảm bảo hai lần sinh 32 byte không bao giờ giống nhau không?**  
   Không có bảo đảm tuyệt đối. Xác suất va chạm rất nhỏ nếu triển khai và entropy tốt, nhưng ràng buộc duy nhất phải được xử lý riêng khi nghiệp vụ yêu cầu.

## 11. Ghi nhớ và tài liệu tham khảo

Khi nhiệm vụ yêu cầu **khó dự đoán**, sử dụng `SecureRandom` với provider đáng tin cậy và seed từ nguồn entropy phù hợp. Dùng `nextBytes` cho secret token dài; dùng `nextInt(bound)` khi cần giá trị trong một miền; không tự modulo để tạo OTP. `SecureRandom` là thành phần **sinh bí mật**, không thay thế xác thực, giới hạn lượt thử, TTL, lưu trữ bí mật an toàn hay khả năng phát hiện lạm dụng.

Javadoc và tài liệu chính thức:

- [Java 21: `java.security.SecureRandom`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html)
- [Java 21: `java.util.Base64`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Base64.html)
- [Java 21: `java.security.MessageDigest`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html)
- [Java 21 Security Developer's Guide](https://docs.oracle.com/en/java/javase/21/security/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-011">Chọn thuật toán, seed và cách kiểm thử mà không nhầm với bảo đảm bảo mật</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn thuật toán, seed và cách kiểm thử số ngẫu nhiên trong Java: phân biệt tái lập với bảo mật

## 1. Ba câu hỏi cần tách riêng

Khi một chương trình cần sinh số ngẫu nhiên, không thể chọn API chỉ dựa trên việc nó trả về số trông có vẻ khó đoán. Trước tiên, hãy xác định:

1. **Dùng vào việc gì?** Mô phỏng, dữ liệu kiểm thử, chọn mẫu, trò chơi, phân tải, hay tạo token/giá trị bí mật?
2. **Có cần chạy lại đúng kết quả không?** Một thí nghiệm hoặc lỗi phát sinh từ số giả ngẫu nhiên thường cần tái lập bằng seed và cùng thứ tự gọi.
3. **Có cần chống kẻ tấn công dự đoán không?** Token phiên đăng nhập, mã đặt lại mật khẩu, khóa bí mật và các giá trị nhạy cảm phải được sinh bởi bộ sinh ngẫu nhiên an toàn mật mã (CSPRNG) phù hợp.

Ba tính chất **có thể kiểm thử được**, **có phân bố thích hợp** và **khó dự đoán trước đối thủ** là các yêu cầu khác nhau. Một danh sách có vẻ lộn xộn không chứng minh an toàn; một chuỗi tái lập chính xác càng không phải bằng chứng an toàn. Seed là thành phần khởi tạo bộ sinh, nhưng không tự biến một thuật toán thành CSPRNG.

Mục tiêu của bài là lựa chọn đúng công cụ Java 17+, quản lý seed có chủ đích, thiết kế kiểm thử đáng tin và tránh những suy luận sai về bảo mật.

## 2. Bản chất pseudorandom, seed và trạng thái

Một bộ sinh số giả ngẫu nhiên (PRNG) là thuật toán có **trạng thái**. Seed được dùng để thiết lập trạng thái ban đầu. Sau mỗi thao tác lấy số, trạng thái được cập nhật; các kết quả tiếp theo phụ thuộc vào trạng thái đó.

~~~text
seed ban đầu
    |
    v
trạng thái S0
    | nextInt(...) hoặc phương thức sinh số
    v
giá trị 1 + trạng thái S1
    | lần lấy tiếp theo
    v
giá trị 2 + trạng thái S2
    |
    v
...
~~~

Đối với một thuật toán xác định, nếu cùng trạng thái ban đầu và thực hiện **cùng trình tự gọi với cùng tham số**, ta có thể tái lập dãy kết quả. Thế nhưng:

- **Cùng seed nhưng khác thuật toán:** không bảo đảm cùng chuỗi.
- **Cùng thuật toán/seed nhưng khác số lần gọi:** các giá trị được gán cho từng bước nghiệp vụ có thể thay đổi.
- **Chia sẻ bộ sinh qua nhiều luồng:** thứ tự luồng tiêu thụ số có thể thay đổi giữa hai lần chạy.
- **Cùng seed nhưng khác phiên bản triển khai:** chỉ dựa vào tính tái lập giữa phiên bản khi hợp đồng cụ thể của bộ sinh bảo đảm điều đó.

Tính tái lập là công cụ để thử nghiệm và chẩn đoán. Tính an toàn mật mã là yêu cầu riêng đối với thuật toán, seed/nguồn entropy, cách dùng và môi trường vận hành.

## 3. Các lựa chọn trong Java 17+

| Công cụ | Phạm vi phù hợp | Seed và tái lập | Giới hạn chính |
| --- | --- | --- | --- |
| `java.util.Random` | Ví dụ, kiểm thử, mô phỏng nhỏ, mã cần tính tương thích với bộ sinh cổ điển | `new Random(long seed)`; cùng chuỗi khi cùng lời gọi; thuật toán cốt lõi của lớp được quy định | Không dùng cho bí mật; dùng chung nhiều luồng có thể tranh chấp và thay đổi thứ tự tiêu thụ |
| `java.util.SplittableRandom` | Mô phỏng cần nhiều luồng sinh số riêng hoặc chia công việc | Constructor nhận seed; có `split()` tạo bộ sinh mới | Không phải CSPRNG; đừng chia sẻ tùy tiện một thể hiện giữa nhiều luồng |
| `java.util.concurrent.ThreadLocalRandom` | Sinh số thông thường thuận tiện trong tác vụ chạy đồng thời | Lấy bằng `ThreadLocalRandom.current()`; không thiết kế để seed thủ công cho từng test | `setSeed(...)` không được hỗ trợ; không phù hợp khi cần tái lập từng chuỗi chính xác bằng seed do test cung cấp |
| `java.util.random.RandomGenerator` và `RandomGeneratorFactory` | Chọn thuật toán cụ thể, thiết kế API nhận bộ sinh từ bên ngoài | Có thể tạo bộ sinh từ tên thuật toán và seed khi factory hỗ trợ | Cần ghi rõ **tên thuật toán**, đặc tính và phiên bản môi trường khi cần replay lâu dài |
| `java.security.SecureRandom` | Token, nonce, salt và giá trị cần khó dự đoán | Thường khởi tạo không truyền seed cố định; hành vi seed và thuật toán phụ thuộc provider | Không coi `setSeed(fixedValue)` là hợp đồng replay; không thay bằng `Random` chỉ để dễ test |

Trong Java 17, `Random`, `SplittableRandom` và `ThreadLocalRandom` thuộc hệ sinh thái `RandomGenerator`. Phân loại ở trên không có nghĩa mọi thuật toán cung cấp cùng chuỗi cho một seed hay có cùng đặc tính thống kê.

**Nguyên tắc chọn nhanh:**

- Cần chuỗi xác định để kiểm thử/mô phỏng đơn giản → chọn `Random` với seed rõ ràng, hoặc một thuật toán xác định đã được ghi tên.
- Cần luồng sinh riêng cho những tác vụ mô phỏng → cân nhắc `SplittableRandom` hoặc những generator hỗ trợ tách/splitting, cấp seed và cấu trúc công việc minh bạch.
- Chỉ cần các giá trị không dùng vào bảo mật trong tác vụ đa luồng và không phải replay theo seed → `ThreadLocalRandom` thường tiện.
- Giá trị có ý nghĩa **bảo mật** → chọn `SecureRandom`; seed cố định và `Random` không phải thay thế hợp lệ.

## 4. Ví dụ Java 17: tái lập số kiểm thử và giữ ranh giới bảo mật

Tạo tệp `RandomChoiceDemo.java`:

~~~java
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class RandomChoiceDemo {
    static List<Integer> sample(Random random, int count) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            numbers.add(random.nextInt(100));
        }
        return numbers;
    }

    static int rollDie(RandomGenerator random) {
        return random.nextInt(1, 7);
    }

    public static void main(String[] args) {
        List<Integer> run1 = sample(new Random(42L), 5);
        List<Integer> run2 = sample(new Random(42L), 5);

        System.out.println("Seed 42: " + run1);
        System.out.println("Same sequence: " + run1.equals(run2));

        Random stateful = new Random(42L);
        System.out.println("First: " + stateful.nextInt(100));
        System.out.println("Second: " + stateful.nextInt(100));
        stateful.setSeed(42L);
        System.out.println("After reset: " + stateful.nextInt(100));

        RandomGenerator g1 = RandomGeneratorFactory
                .of("L64X128MixRandom")
                .create(2026L);
        RandomGenerator g2 = RandomGeneratorFactory
                .of("L64X128MixRandom")
                .create(2026L);
        System.out.println("Named algorithm replay: "
                + (rollDie(g1) == rollDie(g2)));

        SplittableRandom left = new SplittableRandom(123L);
        SplittableRandom right = new SplittableRandom(123L);
        System.out.println("Splittable seeded replay: "
                + (left.nextLong() == right.nextLong()));

        SecureRandom secure = new SecureRandom();
        byte[] secretBytes = new byte[16];
        secure.nextBytes(secretBytes);
        System.out.println("Secure bytes count: " + secretBytes.length);
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac RandomChoiceDemo.java
java RandomChoiceDemo
~~~

Kết quả:

~~~text
Seed 42: [30, 63, 48, 84, 70]
Same sequence: true
First: 30
Second: 63
After reset: 30
Named algorithm replay: true
Splittable seeded replay: true
Secure bytes count: 16
~~~

Giải thích theo từng phần:

1. `new Random(42L)` sinh `[30, 63, 48, 84, 70]` khi gọi `nextInt(100)` năm lần. Cùng seed và cùng cách gọi cho cùng danh sách.
2. `stateful.nextInt(100)` tiêu thụ trạng thái. Sau hai lần gọi, `setSeed(42L)` đặt lại điểm bắt đầu, nên lần tiếp theo trả `30`.
3. `RandomGeneratorFactory.of("L64X128MixRandom")` chọn rõ **thuật toán được cung cấp trong Java 17**, rồi `create(2026L)` tạo hai generator dùng cùng seed. Ví dụ kiểm tra tính tái lập của một lời gọi `nextInt(1, 7)`; nó không tuyên bố dãy này giống `Random(2026L)`.
4. Hai `SplittableRandom(123L)` nhận cùng seed, lấy `nextLong()` đầu tiên nên kết quả bằng nhau. Điều này chỉ chứng minh lần sinh có thể tái lập theo thuật toán, không chứng minh bí mật.
5. `SecureRandom` tạo 16 byte mà chương trình không in giá trị. Chỉ in **độ dài luôn xác định**; các byte thực tế không được kỳ vọng cố định hay dự đoán. Không đưa giá trị nhạy cảm vào stdout/log.

Phương thức `rollDie(RandomGenerator random)` là một ví dụ **tiêm bộ sinh số** (dependency injection đơn giản): logic nghiệp vụ không cần tự quyết định seed. Dùng giao diện giúp chọn generator tại nơi khởi tạo, nhưng vẫn phải tuân thủ yêu cầu của ứng dụng về thuật toán và bảo mật.

## 5. Tái lập cần nhiều hơn một con số seed

Một lần chạy có thể chỉ thực sự tái lập được khi giữ các điều kiện sau:

| Điều kiện | Tại sao cần |
| --- | --- |
| Tên lớp hoặc thuật toán generator | `Random` và `SplittableRandom` không cùng thuật toán |
| Giá trị seed | Khởi tạo trạng thái tương ứng |
| Thứ tự, số lần gọi, tham số bound/origin | Mỗi lời gọi tiêu thụ trạng thái; một số API tiêu thụ nhiều bước |
| Cách phân chia generator cho tác vụ | Tránh khác thứ tự tiêu thụ giữa các tác vụ |
| Mã và phiên bản chạy có liên quan | Thay đổi thuật toán nghiệp vụ có thể thay đổi nơi lấy số |
| Dữ liệu đầu vào và cấu hình | Các nhánh điều kiện khác nhau có thể gọi generator số lần khác nhau |

Ví dụ hai đoạn sau đều dùng `Random(42L)`, nhưng ánh xạ dãy số tới `loot` khác nhau:

~~~java
Random before = new Random(42L);
int enemy = before.nextInt(100); // 30
int loot = before.nextInt(100);  // 63
~~~

~~~java
Random after = new Random(42L);
int enemy = after.nextInt(100);   // 30
int weather = after.nextInt(100); // 63
int loot = after.nextInt(100);    // 48
~~~

Các đoạn trên là **mảnh mã minh họa**; để chạy độc lập cần đặt trong lớp/phương thức và import `java.util.Random`. Việc thêm một lần rút số cho thời tiết làm `loot` lấy một vị trí khác trong chuỗi. Seed không hề thay đổi.

Nếu nhiều phần của mô phỏng cần tách rời, cấp generator riêng cho từng phần với seed được quản lý:

~~~java
Random weatherRandom = new Random(1001L);
Random lootRandom = new Random(2002L);
~~~

Khi đó thao tác lấy thêm số thời tiết không làm tiến trạng thái của `lootRandom`. Tuy nhiên hai seed khác nhau **không tự bảo đảm** tính độc lập thống kê lý tưởng; các thuật toán và chiến lược phân chia stream phải phù hợp yêu cầu mô phỏng.

## 6. Seed cố định, seed tự động và giới hạn của java.util.Random

`Random` cổ điển sử dụng một bộ sinh đồng dư tuyến tính (LCG) với trạng thái **48 bit**. Seed dạng `long` được trộn thành trạng thái theo công thức:

~~~text
state0 = (seed XOR 0x5DEECE66D) AND ((1L << 48) - 1)
stateNext = (state * 0x5DEECE66D + 0xB) mod 2^48
~~~

Về mặt kết quả, seed `42L` và `42L + (1L << 48)` có cùng 48 bit thấp nên cho cùng trạng thái khởi tạo và cùng chuỗi `Random`. Điều này cũng cho thấy **không phải mọi seed `long` khác nhau đều tạo chuỗi khác nhau** đối với lớp này.

`new Random()` sử dụng cơ chế khởi tạo seed tự động, thích hợp khi không cần chủ động ấn định chuỗi; nó **không** là giải pháp bảo mật. `new Random(System.currentTimeMillis())` tạo seed dựa trên đồng hồ, thường dễ đoán và có thể trùng giữa các lần khởi tạo gần nhau. Không dùng ngày giờ hoặc các giá trị hiển nhiên để làm “bí mật” cho token.

Tại sao `Random` không an toàn cho bí mật?

- Thuật toán công khai, trạng thái hữu hạn và mục đích thiết kế là sinh số giả ngẫu nhiên thông thường.
- Một kẻ quan sát có đủ thông tin về đầu ra có thể suy luận về chuỗi, vì vậy không có bảo đảm chống dự đoán tương tự CSPRNG.
- Seed cố định giúp phát lại kết quả, tức là trái với yêu cầu token cần khó đoán và không được lặp lại có hệ thống.

Nhớ rằng **cùng seed → cùng chuỗi** của `Random` là điều hữu ích cho kiểm thử, không phải bằng chứng token đủ ngẫu nhiên trước một đối thủ.

## 7. Bảo mật: dùng SecureRandom đúng mục đích

Với yêu cầu tạo byte mật mã, token hoặc giá trị cần chống suy đoán, dùng `java.security.SecureRandom`:

~~~java
SecureRandom secureRandom = new SecureRandom();
byte[] token = new byte[32]; // 256 bit dữ liệu được sinh
secureRandom.nextBytes(token);
~~~

Đây là mảnh mã đặt trong lớp có import `java.security.SecureRandom`. Nó minh họa tạo **32 byte** bằng một CSPRNG, nhưng chưa bao gồm cách mã hóa token, lưu trữ, thời hạn hiệu lực, xác thực, chống lộ log hay quy trình sử dụng an toàn khác. Độ dài 32 byte tự nó không đủ chứng minh toàn bộ hệ thống an toàn.

Các nguyên tắc:

- Ưu tiên để **provider của Java và hệ điều hành** khởi tạo entropy/trạng thái thích hợp khi dùng `new SecureRandom()`; chọn thuật toán/provider cụ thể khi môi trường hoặc yêu cầu bảo mật quy định.
- Không dùng `new Random(seed)`, `SplittableRandom` hoặc `ThreadLocalRandom` để tạo token đăng nhập, khóa, salt hoặc nonce cần tính khó dự đoán.
- Không dùng một seed cố định để khiến giá trị `SecureRandom` trong môi trường thực chạy lặp lại. `SecureRandom.setSeed(...)` có ngữ nghĩa phụ thuộc provider và thường **bổ sung seed** vào trạng thái thay vì bảo đảm đặt lại trạng thái để replay; không nên khẳng định “cùng seed cho cùng chuỗi” với `SecureRandom`.
- Không ghi log seed/byte bí mật; cũng không in token thực vào log hay test output. Trong khi đó, seed của **mô phỏng không có bí mật** có thể được ghi lại để phục vụ debug.
- Tính an toàn phụ thuộc cả cách sử dụng giá trị ngẫu nhiên: ngữ cảnh, vòng đời, số lượng, lưu trữ, điều kiện duy nhất và hệ thống phân quyền.

Nếu nghiệp vụ yêu cầu token 6 chữ số, một CSPRNG có thể chọn một giá trị số trong khoảng `0` đến `999_999` rồi định dạng đủ sáu chữ số; tuy nhiên token ngắn có ít khả năng thử hơn nên vẫn phải có thời hạn, giới hạn số lần thử và biện pháp phòng thủ thích hợp. Không suy ra “SecureRandom” tự giải quyết toàn bộ những vấn đề đó.

## 8. Những kiểm thử cần thiết cho bộ sinh số

Có ít nhất bốn nhóm mục tiêu kiểm thử, với tiêu chí khác nhau.

### 8.1. Kiểm thử tái lập (reproducibility)

Đối với `Random` với seed cố định:

~~~java
Random a = new Random(42L);
Random b = new Random(42L);

for (int i = 0; i < 100; i++) {
    if (a.nextInt(1000) != b.nextInt(1000)) {
        throw new AssertionError("Different sequences");
    }
}
~~~

Mảnh mã này minh họa kỳ vọng hai đối tượng cùng seed và cùng 100 lời gọi sẽ trùng nhau; đặt trong một phương thức/lớp có import `Random`.

Kiểm thử được ích lợi về tính tái lập và giúp phát hiện thay đổi trong cách mã đang tiêu thụ bộ sinh. Nó **không** kiểm thử chất lượng thống kê hay an toàn mật mã.

### 8.2. Kiểm thử thuộc tính và biên

Với hàm `rollDie(RandomGenerator)` trả số nguyên trong `[1,7)`, có thể kiểm tra **mọi kết quả quan sát** nằm trong `1..6`; với `nextInt(bound)`, bound phải dương. Kiểm thử có giá trị hơn khi xác minh logic sử dụng generator, ví dụ:

- Kết quả không vượt biên và không dưới cận.
- Với bound không hợp lệ, chương trình ném exception thích hợp.
- Không tạo một `Random(fixedSeed)` mới ngay trong mỗi lần rút nếu mục đích là tiêu thụ chuỗi liên tục.
- Nếu nghiệp vụ là xáo trộn danh sách, kết quả là hoán vị hợp lệ, không làm mất hay nhân đôi phần tử.

Không kiểm thử “100 lần chắc chắn phải có đủ cả sáu mặt xúc xắc” vì điều này là một biến cố xác suất, không phải bảo đảm toán học ở mẫu hữu hạn.

### 8.3. Kiểm thử phân bố thống kê

Với mô phỏng, việc kiểm tra tính đều, sai lệch, sự tương quan hoặc chất lượng thuật toán cần phương pháp thống kê phù hợp. Một kiểm thử thô như đếm tần suất sau 20 lần lấy số không đủ kết luận. Các kiểm định phân bố có **xác suất báo động nhầm** và có thể bỏ sót lỗi; không biến chúng thành tiêu chí “100% phải vượt mọi lần chạy” mà không thiết kế ngưỡng và kích thước mẫu cẩn thận.

Với hệ thống quan trọng, có thể kết hợp bộ kiểm định thống kê đã được đánh giá, phân tích đặc tính thuật toán, seed đa dạng và thí nghiệm tái lập. Thống kê về sự đều không thay thế đánh giá bảo mật của CSPRNG.

### 8.4. Kiểm thử hệ thống sử dụng SecureRandom

Một chương trình an toàn không nên viết test kiểu:

~~~java
SecureRandom secure = new SecureRandom();
secure.setSeed(42L);
// Sai mục đích: đòi chuỗi byte bảo mật phải cố định trên mọi provider.
~~~

Vì `SecureRandom` không cung cấp hợp đồng replay chung khi truyền seed theo cách này, việc cố định byte kết quả dễ làm test phụ thuộc provider hoặc cám dỗ thay CSPRNG bằng PRNG thường.

Thay vào đó, kiểm thử **hành vi của ứng dụng**: token có đúng số byte/ký tự, API sử dụng nguồn CSPRNG đã được chọn, cơ chế xác thực và hết hạn hoạt động đúng, lỗi được xử lý, dữ liệu bí mật không vô tình ghi ra ngoài. Với unit test có nhu cầu tách phụ thuộc, dùng một lớp trừu tượng hóa hoặc fake nguồn sinh số trong phạm vi test để điều khiển dữ liệu đầu vào; cấu hình production vẫn dùng `SecureRandom`.

## 9. Thiết kế để dễ kiểm thử: truyền generator vào logic nghiệp vụ

Một hàm trực tiếp tạo `new Random()` bên trong khó tái lập hành vi và dễ làm kết quả kiểm thử không ổn định:

~~~java
// Minh họa điểm bất tiện: trạng thái được khởi tạo bên trong hàm.
static int randomIndex(int size) {
    return new Random().nextInt(size);
}
~~~

Đoạn trên là **mảnh phương thức**. Nếu cần deterministic test, tách lựa chọn generator ra khỏi logic:

~~~java
static int randomIndex(RandomGenerator rng, int size) {
    if (size <= 0) {
        throw new IllegalArgumentException("size must be positive");
    }
    return rng.nextInt(size);
}
~~~

Ví dụ ở Java 17 với `import java.util.random.RandomGenerator;`. Test có thể truyền `new Random(42L)` và kiểm tra chỉ số rút ra; mã sử dụng generator được cung cấp có thể cấu hình khác theo nhu cầu. Không đặt `new Random(42L)` cố định trong code production của một chức năng yêu cầu giá trị khó đoán.

Nếu hàm sinh token, nên tách cấu trúc token khỏi nguồn byte ngẫu nhiên và ép đường production dùng `SecureRandom`/dịch vụ tương đương đã cấu hình đúng. Việc tiêm dependency không được vô tình mở đường dùng bộ sinh không an toàn cho chức năng nhạy cảm.

Một kiểm thử tốt thường có hai tầng:

1. **Unit test logic** dùng đầu vào giả/điều khiển được để kiểm tra định dạng, ranh giới và xử lý lỗi.
2. **Integration/security validation** kiểm tra cấu hình thực tế sử dụng CSPRNG theo yêu cầu, cùng các kiểm soát bảo mật xung quanh.

Không cần “dự đoán đúng byte SecureRandom sẽ sinh” để chứng minh logic gọi API đúng.

## 10. Đa luồng và lựa chọn chiến lược seed

Trong mô phỏng song song, chỉ cấp một `Random(42L)` dùng chung cho mọi luồng không đủ bảo đảm từng tác vụ sẽ nhận cùng dữ liệu trong mỗi lần chạy. Nếu luồng A và B gọi xen kẽ theo thứ tự khác, các giá trị được gán cho A/B cũng khác. Tính an toàn trong tương tác nhiều luồng và tính tái lập theo **tác vụ** là hai điều riêng.

Một chiến lược để kiểm thử:

- Gán mỗi tác vụ một **mã định danh ổn định**.
- Ánh xạ mã đó tới seed riêng theo một quy tắc đã thiết kế và ghi lại.
- Tạo generator riêng bên trong tác vụ; tránh phụ thuộc thứ tự scheduler lấy số.
- Nếu dùng `SplittableRandom.split()`, việc chia các generator thành cây cũng phải được thực hiện theo **cấu trúc và thứ tự ổn định** nếu muốn tái lập chính xác kết quả từng tác vụ.

Không mặc định các seed như `baseSeed + taskIndex` cho những chuỗi độc lập về thống kê một cách hoàn hảo; cần chọn phương án seeding và generator phù hợp với chất lượng mô phỏng yêu cầu. Nếu dùng `ThreadLocalRandom`, cần chấp nhận rằng API không hỗ trợ đặt seed của từng luồng qua `setSeed` để phát lại như `Random`.

Trong Java 17, `RandomGeneratorFactory` hỗ trợ lựa chọn thuật toán theo tên hoặc thuộc tính; hãy kiểm tra thuật toán thực sự có sẵn trong runtime và ghi lại lựa chọn khi cần tái lập dài hạn. Không phải mọi generator có cùng đặc tính phân bố, phân tách và hiệu năng.

## 11. Khi nào seed là dữ liệu nên ghi, khi nào không?

Với **kiểm thử/mô phỏng không nhạy cảm**, ghi seed thường là thực hành tốt:

~~~text
simulation=inventory-replay
prng=java.util.Random
seed=42
draws=5 x nextInt(100)
result=[30, 63, 48, 84, 70]
~~~

Ví dụ log này không chứa bí mật và cho người khác thông tin cần để kiểm tra lại kết quả. Nếu chương trình thay đổi nhánh xử lý, cần ghi thêm phiên bản hoặc các sự kiện sử dụng số ngẫu nhiên.

Với **giá trị phục vụ bảo mật**, tình huống khác hoàn toàn: seed hoặc trạng thái nội bộ có thể là thông tin bí mật; không ghi lại hay chia sẻ để “tái lập token”. Chỉ log thông tin vận hành cần thiết và đã được kiểm soát, chẳng hạn sự kiện phát hành token thành công, mã tương quan không nhạy cảm hoặc thời điểm hết hạn, theo chính sách bảo mật.

Tránh trộn quy tắc quan sát của test và production. Kể cả khi một seed không nhạy cảm ở môi trường thử nghiệm, việc tái sử dụng chính seed này để tạo token thực vẫn sai mục đích.

## 12. Những lỗi phổ biến

**Lỗi 1 — “Cùng seed tức là đủ bảo đảm cùng dữ liệu.”** Sai nếu thay thuật toán, đổi thứ tự gọi, bổ sung lần rút số hoặc để nhiều luồng tranh cùng một generator. Cần ghi cả ngữ cảnh tiêu thụ.

**Lỗi 2 — “Đã cố định seed, vậy token sẽ an toàn.”** Seed cố định làm kết quả dễ tái lập. Thuật toán `Random` không có bảo đảm an toàn mật mã; dùng `SecureRandom` cho bí mật.

**Lỗi 3 — “SecureRandom có setSeed nên test có thể khôi phục cùng chuỗi ở mọi máy.”** Không có hợp đồng chung như vậy. Provider có thể có thuật toán và cơ chế bổ sung seed khác nhau.

**Lỗi 4 — “Khác seed phải khác mọi số.”** Hai chuỗi có thể trùng một vài kết quả ngẫu nhiên; với `Random` 48 bit, nhiều seed `long` còn có thể dẫn đến cùng trạng thái ban đầu. Không dùng “mỗi lần tạo generator phải ra số đầu tiên khác” như một phép kiểm thử đúng/sai.

**Lỗi 5 — Gọi `new Random(seed)` trong vòng lặp.** Mỗi đối tượng bắt đầu lại ở đầu chuỗi, dễ tạo cùng số đầu tiên lặp nhiều lần. Nếu cần một chuỗi, tạo một đối tượng rồi tiêu thụ liên tục.

**Lỗi 6 — Nhầm khoảng của `nextInt(bound)`.** `nextInt(6)` sinh 0..5; xúc xắc 1..6 cần cộng 1 hoặc dùng `nextInt(1, 7)` ở API hỗ trợ.

**Lỗi 7 — Kiểm thử phân bố bằng một số ít lần rút.** Một mẫu ngắn có thể lệch tự nhiên. Cần kiểm định thống kê có thiết kế phù hợp, không áp quy tắc tùy tiện.

**Lỗi 8 — Dùng `ThreadLocalRandom.setSeed` để tái lập.** API chủ đích không hỗ trợ đặt seed sau khi lấy instance; việc gọi sẽ ném `UnsupportedOperationException`.

**Lỗi 9 — Không phân biệt log seed mô phỏng và log bí mật.** Ghi seed cho mô phỏng thông thường giúp debug; ghi seed/trạng thái CSPRNG hoặc token bí mật có thể làm lộ hệ thống.

**Lỗi 10 — Đổi bộ sinh để tăng tốc mà không xem lại các giả định.** Kết quả, chất lượng thống kê, khả năng tách luồng và tính tương thích của replay có thể đổi; cần đánh giá theo yêu cầu.

## 13. Bài luyện tập có lời giải

### Bài 1. Chọn bộ sinh theo tình huống

| Tình huống | Lựa chọn phù hợp | Lý do |
| --- | --- | --- |
| Sinh năm số kiểm thử cần phát lại chính xác | `new Random(42L)` và cùng lời gọi | Thuận tiện kiểm thử xác định |
| Mô phỏng lớn cần generator riêng cho các nhánh | `SplittableRandom` hoặc generator hỗ trợ split | Có thể phân chia bộ sinh và quản lý trạng thái theo tác vụ |
| Tác vụ đa luồng cần số thông thường, không cần đặt seed | `ThreadLocalRandom.current()` | Phù hợp cách dùng theo luồng |
| Tạo token khôi phục tài khoản | `SecureRandom` | Yêu cầu nguồn sinh khó dự đoán |
| Chọn thuật toán có tên để quản lý cấu hình thí nghiệm | `RandomGeneratorFactory.of(name)` | Chọn thuật toán rõ ràng, ghi nhận để replay |

**Điểm đánh giá:** Lựa chọn chỉ hợp lý khi đi kèm các yêu cầu về replay, phân bố và bảo mật; không có một bộ sinh tối ưu cho tất cả bài toán.

### Bài 2. Dự đoán kết quả khi gọi cùng seed

~~~java
Random a = new Random(42L);
Random b = new Random(42L);

System.out.println(a.nextInt(100));
System.out.println(a.nextInt(100));
System.out.println(b.nextInt(100));
~~~

Đáp án:

~~~text
30
63
30
~~~

Vì `a` đã tiêu thụ hai vị trí, còn `b` mới tiêu thụ vị trí đầu tiên.

### Bài 3. Sửa một test không ổn định

Giả sử `calculateDiscount()` gọi `new Random()` bên trong, khiến test không biết bộ sinh sẽ đưa về giá trị nào. Hãy sửa thiết kế để hàm nhận `RandomGenerator` hoặc một giao diện cung cấp giá trị ngẫu nhiên từ ngoài. Trong test, truyền bộ sinh với seed đã biết; trong production, cấu hình bộ sinh phù hợp với yêu cầu nghiệp vụ. Nếu giảm giá phải chống người dùng dự đoán để khai thác, hãy thiết kế kiểm soát nghiệp vụ và nguồn ngẫu nhiên an toàn chứ không chỉ thay seed.

### Bài 4. Phân tích một lời khẳng định

“Dãy token sinh ra đã vượt kiểm định tần suất, nên chắc chắn khó đoán.”

**Đáp án:** Không đúng. Một thuật toán xác định đơn giản có thể cho tần suất trông đều mà vẫn đoán được khi biết seed hoặc trạng thái. Kiểm định tần suất không thay thế phân tích và lựa chọn CSPRNG.

### Bài 5. Ghi nhật ký tái lập

Một lỗi xuất hiện sau khi chia mô phỏng thành bốn tác vụ chạy song song. Chỉ ghi `seed=100` có đủ không?

**Đáp án:** Chưa đủ. Cần biết generator/thuật toán, cách gán hoặc split seed cho từng tác vụ, thứ tự tiêu thụ số (hoặc cách tách trạng thái độc lập), phiên bản chương trình và đầu vào. Nếu mỗi tác vụ có generator riêng và thứ tự xử lý được thiết kế độc lập lịch scheduler, kết quả sẽ dễ tái lập hơn.

## 14. Checklist trước khi chốt thiết kế

1. **Mục đích:** Số dùng cho mô phỏng/kiểm thử hay một bí mật cần chống dự đoán?
2. **Thuật toán:** Chọn rõ `Random`, `SplittableRandom`, một `RandomGenerator` theo tên, `ThreadLocalRandom` hay `SecureRandom`.
3. **Seed:** Ai cung cấp? Có cố định để replay? Có liên quan dữ liệu bí mật không?
4. **Tính tái lập:** Cần cố định những lời gọi, bound, thứ tự, tác vụ và phiên bản nào?
5. **Kiểm thử:** Đang kiểm tra logic, dãy tái lập, phân bố thống kê hay biện pháp bảo mật?
6. **Đồng thời:** Có chia sẻ generator giữa nhiều luồng không; lịch xử lý có làm thay đổi vị trí lấy số không?
7. **Vận hành:** Thông tin nào được phép ghi log, và thông tin bí mật nào bắt buộc không lưu?

**Ghi nhớ:** Seed là công cụ **khởi tạo và tái lập trạng thái** của PRNG; lựa chọn thuật toán quyết định cách sinh chuỗi và các đặc tính cần đánh giá. Kiểm thử được bằng seed cố định **không có nghĩa** an toàn mật mã. Trong Java, hãy chọn `Random`/generator thích hợp cho mô phỏng có thể tái lập, và `SecureRandom` cho giá trị cần khó dự đoán, đồng thời kiểm thử đúng hành vi ứng dụng và kiểm soát bảo mật riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-numbers-012">Stream giá trị ngẫu nhiên và giới hạn kích thước</a>

<details>
<summary>Xem chi tiết</summary>

# Stream giá trị ngẫu nhiên và giới hạn kích thước trong Java

Khi cần tạo 5 mặt xúc xắc, 1.000 điểm dữ liệu thử nghiệm hoặc một luồng số ngẫu nhiên để xử lý từng phần, Java có thể tạo trực tiếp `IntStream`, `LongStream` và `DoubleStream`. Thay vì tạo và lưu một mảng rất lớn từ đầu, ta khai báo nguồn phát số, giới hạn số phần tử và gắn các phép biến đổi trước khi thực thi.

Có hai câu hỏi phải tách biệt:

1. **Phạm vi giá trị:** Mỗi số được lấy trong khoảng nào? Ví dụ xúc xắc cần số nguyên từ 1 đến 6, tức khoảng Java `[1, 7)`.
2. **Kích thước luồng:** Tạo bao nhiêu phần tử? Ví dụ 5 lần tung xúc xắc, tức `streamSize = 5` hoặc `limit(5)`.

Phạm vi giá trị (`origin`, `bound`) và kích thước luồng (`streamSize`, `limit`) hoàn toàn khác nhau. Bài này trình bày cách tạo nguồn số ngẫu nhiên Java 17+, cách giới hạn luồng, ảnh hưởng của thứ tự các phép toán, cơ chế lazy, tài nguyên và những lỗi thường gặp.

## 1. Stream số ngẫu nhiên là gì?

Java Stream là một **pipeline xử lý dữ liệu**, không phải một collection chứa sẵn các phần tử. Với số ngẫu nhiên, nguồn như `Random.ints()` cung cấp các giá trị khi pipeline cần chúng. Phần xử lý có thể gồm `map`, `filter`, `limit`; terminal operation như `toArray`, `sum`, `forEach` hay `count` kích hoạt pipeline.

~~~text
Random / RandomGenerator
          |
          v
  ints / longs / doubles
          |
          v
 filter / map / limit / skip  (nếu có)
          |
          v
  toArray / sum / forEach ...  (terminal)
~~~

Ba dạng primitive stream tránh phải đóng hộp từng `int`, `long` và `double` thành đối tượng `Integer`, `Long`, `Double` trong nhiều phép toán.

| Nguồn | Kiểu stream | Kiểu phần tử |
|---|---|---|
| `random.ints(...)` | `IntStream` | `int` |
| `random.longs(...)` | `LongStream` | `long` |
| `random.doubles(...)` | `DoubleStream` | `double` |
| `Stream.generate(supplier)` | `Stream<T>` | Kiểu đối tượng được `Supplier` sinh ra |

Một luồng ngẫu nhiên **có thể hữu hạn hoặc hiệu quả là vô hạn**. JavaDoc gọi các overload không nhận kích thước là *effectively unlimited*: chúng được thiết kế để cung cấp một lượng giá trị rất lớn cho đến khi pipeline dừng, chứ không có nghĩa chương trình được cấp bộ nhớ vô hạn hay phải tạo vô số đối tượng ngay khi gọi phương thức.

**Lưu ý:** Các số do `Random` tạo là *pseudorandom* (giả ngẫu nhiên). Java Stream không làm cho chúng trở thành số ngẫu nhiên mật mã. Hành vi xác suất, tái lập bằng seed và tính an toàn cho tác vụ bảo mật là những vấn đề riêng với kích thước luồng.

## 2. Các overload của Random: hữu hạn và không giới hạn sẵn

`java.util.Random` cung cấp các nhóm overload tương tự cho `ints`, `longs` và `doubles`:

| Phương thức mẫu | Số lượng phần tử | Khoảng giá trị |
|---|---|---|
| `random.ints()` | Không giới hạn sẵn | Toàn miền `int` |
| `random.ints(streamSize)` | Chính xác `streamSize` trước các phép loại bỏ | Toàn miền `int` |
| `random.ints(origin, bound)` | Không giới hạn sẵn | `origin <= x < bound` |
| `random.ints(streamSize, origin, bound)` | Chính xác `streamSize` trước các phép loại bỏ | `origin <= x < bound` |
| `random.longs(...)` | Cùng bốn dạng overload | Số `long` |
| `random.doubles(...)` | Cùng bốn dạng overload | Số `double` |

Với `doubles()` hay `doubles(streamSize)` không truyền khoảng, mỗi phần tử nằm trong `[0.0, 1.0)`. Với `ints()` và `longs()` không truyền khoảng, nguồn phát giá trị trên toàn miền của kiểu nguyên tương ứng.

Ví dụ:

~~~java
Random random = new Random();

IntStream anyIntegers = random.ints(8);
IntStream dice = random.ints(8, 1, 7);
LongStream ids = random.longs(8, 100L, 1000L);
DoubleStream fractions = random.doubles(8); // 0.0 <= x < 1.0
~~~

Các dòng trên là đoạn minh họa bên trong một phương thức, cần import `java.util.Random` và `java.util.stream.*`. Chúng mô tả **bốn stream tách biệt**. Stream chỉ nên được dùng cho một terminal operation; hãy tạo stream mới khi cần tính lại.

Không nhầm `random.ints(5)` với `random.ints(0, 5)`:

- `ints(5)` có **một tham số kích thước kiểu `long`**, tạo 5 số `int` trên toàn miền giá trị.
- `ints(0, 5)` có **hai tham số phạm vi kiểu `int`**, tạo luồng không giới hạn sẵn các số 0, 1, 2, 3 hoặc 4.
- `ints(5, 0, 5)` có **ba tham số**, tạo đúng 5 số trong `[0, 5)`.

Đây là lỗi rất dễ xảy ra vì `5` có thể được hiểu là số lượng hoặc một biên phạm vi tùy chữ ký phương thức.

## 3. Giới hạn kích thước ngay tại nguồn bằng streamSize

Muốn lấy đúng `n` lần sinh giá trị, cách ngắn gọn là dùng overload với `streamSize`:

~~~java
int[] fiveDice = new Random(42L)
        .ints(5, 1, 7)
        .toArray();
~~~

`fiveDice.length == 5`. Mọi phần tử thỏa `1 <= x && x < 7`. Tham số đầu tiên là **số phần tử**, hai tham số còn lại là **biên dưới bao gồm, biên trên loại trừ**.

Ví dụ về sự khác nhau giữa `streamSize` và khoảng:

~~~java
new Random().ints(5, 1, 7);    // Nguồn hữu hạn: 5 số trong [1,7)
new Random().ints(100, 1, 7);  // Nguồn hữu hạn: 100 số trong [1,7)
new Random().ints(5, 10);      // Nguồn không giới hạn sẵn: số trong [5,10)
~~~

Ba dòng khai báo trên chưa chạy terminal operation, vì vậy chúng không phải ví dụ in dữ liệu. Không nên tạo stream rồi vứt đi trong mã ứng dụng; ví dụ nhằm chỉ rõ ý nghĩa overload.

`streamSize` thuộc kiểu `long` và phải **không âm**. `streamSize = 0` tạo stream rỗng; giá trị âm làm ném `IllegalArgumentException`. Kích thước rất lớn tuy hợp lệ về mặt tham số có thể vẫn không khả thi nếu gọi `toArray()` hoặc gom tất cả phần tử vào bộ nhớ. Với xử lý tuần tự từng phần, `forEach` hoặc một phép gộp có thể tiêu thụ ít bộ nhớ hơn nhưng vẫn cần thời gian tương ứng số phần tử.

**Khi nào chọn overload hữu hạn?** Khi yêu cầu đã biết trước số mẫu, ví dụ tạo 500 giá trị làm fixture kiểm thử hoặc 20 giá trị cho báo cáo, cách này biểu đạt ý định rõ nhất.

## 4. Tạo luồng không giới hạn sẵn rồi dùng limit(n)

Phương thức `limit(long maxSize)` là một *intermediate operation* tạo pipeline có tối đa `maxSize` phần tử từ nguồn phía trước:

~~~java
int[] fiveDice = new Random(42L)
        .ints(1, 7)     // nguồn hiệu quả là vô hạn
        .limit(5)       // giới hạn tối đa 5 phần tử
        .toArray();     // kích hoạt sinh và thu thập
~~~

Với nguồn giả ngẫu nhiên luôn có đủ giá trị, `limit(5)` và không có phép loại bỏ sau đó sẽ tạo đúng 5 phần tử. Nhưng quy tắc tổng quát của `limit(n)` là **tối đa n**, không phải lúc nào cũng đúng n: nếu nguồn đã chỉ có 3 phần tử thì `limit(5)` vẫn chỉ thu được 3 phần tử.

Hai cách sau đều thể hiện ý định tạo 5 số thuộc `[1,7)`:

~~~java
new Random(42L).ints(5, 1, 7).toArray();        // Giới hạn ở nguồn
new Random(42L).ints(1, 7).limit(5).toArray();  // Giới hạn ở pipeline
~~~

Kết quả số cụ thể có thể khác theo thuật toán/kiểu bộ sinh và cách tiêu thụ nguồn, nhất là với parallel stream; với `java.util.Random` và cùng chuỗi gọi trên luồng tuần tự, seed cho phép tái lập chuỗi. **Chọn cách diễn đạt đúng quy tắc**, không chọn vì trông giống một dãy số cụ thể.

`limit(0)` trả stream rỗng; `limit(-1)` làm ném `IllegalArgumentException`. Nếu `n` do người dùng cung cấp, hãy kiểm tra `0 <= n <= maxAllowed` trước khi dựng pipeline, thay vì cho phép một yêu cầu khổng lồ chiếm CPU/bộ nhớ.

## 5. Ví dụ chạy độc lập Java 17+

Tạo tệp `RandomStreamSizeDemo.java`:

~~~java
import java.util.Arrays;
import java.util.Random;

public class RandomStreamSizeDemo {
    public static void main(String[] args) {
        int[] five = new Random(42L).ints(5, 1, 7).toArray();
        System.out.println("five = " + Arrays.toString(five));
        System.out.println("five length = " + five.length);
        System.out.println("five bounds = "
                + Arrays.stream(five).allMatch(x -> x >= 1 && x < 7));

        int[] four = new Random(99L)
                .ints(0, 100)
                .limit(4)
                .toArray();
        System.out.println("four = " + Arrays.toString(four));
        System.out.println("four length = " + four.length);

        int[] tenEven = new Random(7L)
                .ints(0, 100)
                .filter(x -> x % 2 == 0)
                .limit(10)
                .toArray();
        System.out.println("tenEven length = " + tenEven.length);
        System.out.println("tenEven bounds = "
                + Arrays.stream(tenEven)
                        .allMatch(x -> x >= 0 && x < 100 && x % 2 == 0));

        int[] finiteFiltered = new Random(5L)
                .ints(6, 0, 10)
                .filter(x -> x >= 5)
                .toArray();
        System.out.println("finiteFiltered = "
                + Arrays.toString(finiteFiltered));
        System.out.println("finiteFiltered length = " + finiteFiltered.length);

        System.out.println("empty size = "
                + new Random(8L).ints(0, 10, 11).count());

        long[] longs = new Random(4L).longs(3, 10L, 20L).toArray();
        double[] doubles = new Random(4L)
                .doubles(3, 0.5, 1.5)
                .toArray();
        System.out.println("long bounds = "
                + Arrays.stream(longs).allMatch(x -> x >= 10L && x < 20L));
        System.out.println("double bounds = "
                + Arrays.stream(doubles).allMatch(x -> x >= 0.5 && x < 1.5));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RandomStreamSizeDemo.java
java RandomStreamSizeDemo
~~~

Một kết quả với `java.util.Random` theo thuật toán chuẩn:

~~~text
five = [3, 4, 1, 3, 1]
five length = 5
five bounds = true
four = [87, 58, 29, 11]
four length = 4
tenEven length = 10
tenEven bounds = true
finiteFiltered = [7, 6, 5]
finiteFiltered length = 3
empty size = 0
long bounds = true
double bounds = true
~~~

Ý nghĩa các phần:

1. `ints(5, 1, 7)` được cấp kích thước ở **nguồn**, nên thu được 5 số có mặt từ 1 đến 6. Giá trị xuất hiện lại, như số 3 và số 1, là hợp lệ: sinh ngẫu nhiên **không có nghĩa không trùng lặp**.
2. `ints(0, 100).limit(4)` dùng nguồn không giới hạn sẵn rồi lấy 4 phần tử.
3. `filter(even).limit(10)` tiếp tục sinh từ nguồn không giới hạn sẵn đến khi **nhận đủ 10 số chẵn** trong ví dụ này; số lần thử có thể lớn hơn 10.
4. `ints(6, 0, 10).filter(x >= 5)` bắt đầu từ **6 phần tử nguồn**, sau lọc chỉ còn 3 phần tử đối với seed đã chọn. Đặt kích thước ở nguồn không có nghĩa kết quả cuối sau `filter` vẫn phải chứa 6 phần tử.
5. `ints(0, 10, 11)` dùng `streamSize = 0`; terminal `count()` trả 0. Hai nguồn `longs`/`doubles` cũng hoạt động theo kích thước/phạm vi độc lập.

Ví dụ trên sử dụng seed chỉ để làm đầu ra dễ kiểm chứng. Không dùng seed cố định cho mục đích bảo mật hoặc trong sản phẩm khi yêu cầu dữ liệu khó đoán.

## 6. Phạm vi giá trị: origin bao gồm, bound loại trừ

Với overload có khoảng, quy tắc cơ bản là:

~~~text
origin <= value < bound
~~~

Ví dụ:

| Ý định | Lời gọi |
|---|---|
| Tung xúc xắc 20 lần, mỗi lần 1–6 | `random.ints(20, 1, 7)` |
| Lấy 10 số nguyên từ 0 đến 9 | `random.ints(10, 0, 10)` |
| Lấy 4 số nguyên từ -3 đến 3 | `random.ints(4, -3, 4)` |
| Lấy 5 số `long` trong [100, 200) | `random.longs(5, 100L, 200L)` |
| Lấy 6 số `double` trong [0.25, 0.75) | `random.doubles(6, 0.25, 0.75)` |

Truyền `origin >= bound` vào overload có khoảng gây `IllegalArgumentException`; việc đặt hai biên bằng nhau không tạo dòng toàn một số như nhiều người lầm tưởng. Đối với `double`, biên phải hữu hạn và khoảng hợp lệ; không dùng `Double.NaN` hoặc vô cực làm biên.

Cẩn thận khi muốn "bao gồm giá trị tối đa": với `int`, không cộng bừa `1` vào `Integer.MAX_VALUE`, vì tràn số sẽ làm hỏng khoảng. Tương tự, không dùng `(int)(Math.random() * range)` cho khoảng lớn nếu điều này gây lệch phân bố hoặc tràn số. Ưu tiên overload có `origin`/`bound` và kiểm tra rõ tình huống phải bao gồm biên cực đại.

Một khoảng có chỉ **một giá trị hợp lệ**, ví dụ `[5,6)`, sẽ cho toàn số 5. Đó là đúng hợp đồng, dù không tạo sự đa dạng. Muốn nhiều số khác nhau, cần khoảng đủ rộng và vẫn có thể xuất hiện trùng lặp.

## 7. Thứ tự filter và limit quyết định kích thước kết quả

`filter` loại các phần tử không đạt điều kiện; `limit` chặn số phần tử đi qua **đến vị trí đặt nó** trong pipeline. Vì thế hai chuỗi sau không tương đương:

~~~java
// A: lấy tối đa 10 phần tử nguồn, sau đó lọc chẵn
int[] a = new Random(7L)
        .ints(0, 100)
        .limit(10)
        .filter(x -> x % 2 == 0)
        .toArray();

// B: lọc chẵn trước, rồi lấy tối đa 10 số chẵn
int[] b = new Random(7L)
        .ints(0, 100)
        .filter(x -> x % 2 == 0)
        .limit(10)
        .toArray();
~~~

Các đoạn này cần `import java.util.Random;` và được đặt trong phương thức.

| Pipeline | Giới hạn đặt lên | Số phần tử đầu ra |
|---|---|---|
| `limit(10).filter(even)` | Tối đa 10 số nguồn | Có thể từ 0 đến 10 |
| `filter(even).limit(10)` | Tối đa 10 số **đã lọc** | 10 nếu nguồn cuối cùng cung cấp đủ số thỏa điều kiện và terminal hoàn tất |
| `ints(10, 0, 100).filter(even)` | Chính xác 10 số nguồn | Có thể từ 0 đến 10 |
| `ints(10, 0, 100)` | 10 số nguồn và không loại bỏ | Đúng 10 |

**Quy tắc ứng dụng:** nếu bài toán là **thử đúng 10 lần** rồi lấy những kết quả hợp lệ, đặt giới hạn nguồn trước `filter`. Nếu bài toán là **tìm tối đa 10 kết quả hợp lệ**, đặt `filter` trước `limit` và cân nhắc giới hạn số lần thử/tổng thời gian.

Với nguồn không giới hạn sẵn, `filter(...).limit(10)` có thể mất thời gian rất lâu hoặc không kết thúc nếu điều kiện không bao giờ đúng. Ví dụ `ints(1, 7).filter(x -> x == 7).limit(1)` sẽ không bao giờ tìm ra 7 vì nguồn chỉ sinh 1–6.

Để vừa giới hạn **số lần thử** vừa giới hạn **số kết quả hợp lệ**, dùng:

~~~java
int[] accepted = new Random()
        .ints(100, 0, 100)          // thử tối đa 100 mẫu
        .filter(x -> x > 95)        // chỉ giữ 96..99
        .limit(5)                   // tối đa 5 kết quả
        .toArray();
~~~

Kết quả có thể ít hơn 5 số nếu không đủ mẫu hợp lệ. Đây là cách xử lý có giới hạn chắc chắn về số mẫu được xét, thay vì chỉ dựa vào xác suất điều kiện cuối cùng xảy ra.

## 8. Stream là lazy: khi nào bộ sinh thực sự hoạt động?

Đoạn sau mới chỉ **mô tả pipeline**:

~~~java
Random random = new Random(42L);
IntStream stream = random.ints(1, 7).limit(3);
~~~

Nguồn phát số khi phần tử được yêu cầu trong terminal operation. Khi gọi:

~~~java
int[] values = stream.toArray();
~~~

pipeline mới cần tiêu thụ số từ nguồn.

Một điều dễ nhầm: `count()` không luôn buộc pipeline phải gọi bộ sinh từng lần. Một stream hữu hạn đã biết kích thước có thể cho phép thư viện tối ưu `count()` mà không cần tạo các phần tử. Vì vậy, **không dùng số lần chạy của `peek()` hoặc trạng thái bộ sinh sau `count()` để kiểm chứng việc nguồn đã phát bao nhiêu số**. Nếu cần kiểm tra giá trị thật, dùng `toArray()`, `forEach()` hoặc tính toán thực tế phụ thuộc giá trị.

Sau khi đã thực hiện terminal operation, stream đó **không thể tái sử dụng**:

~~~java
IntStream numbers = new Random().ints(5);
int[] first = numbers.toArray();

// numbers.sum(); // IllegalStateException: stream đã được dùng
~~~

Hãy tạo pipeline mới từ nguồn hoặc dùng supplier trả stream mới nếu cần chạy lại. Nếu cả hai pipeline dùng chung một đối tượng `Random`, trạng thái bộ sinh đã tiến lên khi được tiêu thụ; kết quả lần hai thường khác lần một.

## 9. Stream.generate và Math.random có thể dùng khi nào?

Ngoài `Random.ints(...)`, Java hỗ trợ:

~~~java
import java.util.stream.Stream;
import java.util.concurrent.ThreadLocalRandom;

int[] dice = Stream.generate(
        () -> ThreadLocalRandom.current().nextInt(1, 7))
        .limit(5)
        .mapToInt(Integer::intValue)
        .toArray();
~~~

`Stream.generate(Supplier<T>)` tạo một stream đối tượng **không giới hạn sẵn**; `limit(5)` biến phần cần thu thập thành hữu hạn. Với số primitive, `random.ints(5, 1, 7)` thường ngắn hơn, biểu đạt miền giá trị và tránh boxing/unboxing. `Stream.generate` có ích nếu phần tử phải là đối tượng phức tạp, ví dụ bộ dữ liệu giả lập gồm nhiều trường.

`Math.random()` trả một `double` từ 0 (bao gồm) đến 1 (loại trừ). Có thể viết `Stream.generate(Math::random).limit(5)` nhưng nó là `Stream<Double>` có boxing, và cách điều khiển seed không trực tiếp như tạo `new Random(seed)`. Không dùng `(int)(Math.random() * n)` một cách máy móc khi đã có API sinh số nguyên theo khoảng.

Từ Java 17, giao diện `java.util.random.RandomGenerator` chuẩn hóa API cho nhiều thuật toán bộ sinh. `java.util.Random` triển khai giao diện này. Khi cần lựa chọn thuật toán phù hợp hoặc loại bộ sinh đặc biệt để chạy song song, có thể thiết kế theo `RandomGenerator`; **bất kể bộ sinh nào, vẫn cần kiểm tra kích thước, khoảng và chất lượng ngẫu nhiên đúng mục đích**.

## 10. Seed và khả năng tái lập trong kiểm thử

Seed giúp kết quả giả ngẫu nhiên có thể được tạo lại khi giữ đúng thuật toán, cách gọi và thứ tự tiêu thụ. Ví dụ:

~~~java
int[] left = new Random(123L).ints(5, 0, 10).toArray();
int[] right = new Random(123L).ints(5, 0, 10).toArray();

System.out.println(Arrays.equals(left, right)); // true
~~~

Thêm `import java.util.Arrays;` bên cạnh `java.util.Random`.

Với `java.util.Random` cụ thể, thuật toán được Java xác định để bảo đảm tính lặp lại khi hai instance có seed giống nhau và cùng chuỗi thao tác. Tuy nhiên, không giả định mọi lớp triển khai `RandomGenerator` phải cho **cùng** dãy số với `Random`, hoặc cùng seed nhưng đổi thuật toán/parallelism sẽ cho cùng chuỗi.

Khi viết test, ưu tiên xác minh tính chất:

- **Kích thước:** đúng số phần tử cần tạo hoặc không vượt giới hạn áp đặt.
- **Biên:** mọi giá trị thuộc `[origin, bound)`.
- **Dữ liệu hợp lệ:** `filter`, `map` và các ràng buộc đều được đáp ứng.
- **Khả năng tái lập:** dùng seed và bộ sinh có xác định khi chính xác chuỗi là điều cần kiểm chứng.

Không dùng seed cố định để tạo token đăng nhập, mật khẩu tạm hoặc giá trị bảo mật. `Random` không được thiết kế để chống dự đoán; các trường hợp bảo mật cần API `SecureRandom` và thiết kế phù hợp.

## 11. Giới hạn kích thước và quản lý tài nguyên

**Không thu thập luồng không giới hạn sẵn nếu chưa chặn.** Ví dụ dưới đây nguy hiểm:

~~~java
// Không chạy:
new Random().ints().toArray();
~~~

Mảng cần được tạo có kích thước không có giới hạn khả thi và chương trình có thể hết bộ nhớ hoặc không thể hoàn tất. Một pipeline không giới hạn sẵn với phép toán terminal như `sum()` hoặc `forEach()` cũng có thể không kết thúc nếu không có điều kiện chặn.

Nếu chỉ cần một giá trị, dùng `random.nextInt(origin, bound)` của API phù hợp hoặc `random.ints(origin, bound).findAny()`, thay vì tạo tập lớn. Với một batch 50.000 phần tử mà chỉ cần thống kê, có thể xử lý trực tiếp:

~~~java
long numberOfEven = new Random()
        .ints(50_000, 0, 1_000)
        .filter(x -> x % 2 == 0)
        .count();
~~~

Do `filter` loại phần tử tùy giá trị, ở đây terminal phải xác định số phần tử đạt điều kiện; tổng số đầu vào hữu hạn rõ ràng. Không dùng `count()` của pipeline đơn thuần để kết luận tất cả phần tử đã được hiện thực hóa.

**Giới hạn do người dùng nhập:** kiểm tra giới hạn kích thước theo tài nguyên trước khi gọi nguồn. Một số `streamSize` hợp lệ kiểu `long` có thể vượt khả năng tạo mảng Java; `limit` không đồng nghĩa miễn phí về bộ nhớ nếu tiếp theo gọi `toArray()`.

**Không sắp xếp toàn bộ nguồn vô hạn:** `random.ints().sorted().limit(10)` cần biết thứ tự toàn bộ đầu vào trước khi chọn phần tử theo thứ tự nên không hoàn tất hữu ích. Ngược lại:

~~~java
int[] orderedSample = new Random()
        .ints(0, 100)
        .limit(10)
        .sorted()
        .toArray();
~~~

Cách này lấy mẫu hữu hạn **trước**, rồi sắp xếp chính các mẫu đã lấy. Nó không tìm ra "10 số nhỏ nhất trong vô hạn số" và cũng không bảo đảm không trùng lặp.

**`distinct()` và miền hữu hạn:** nguồn `ints(1, 7)` chỉ có 6 giá trị phân biệt. `distinct().limit(7)` trên nguồn này sẽ không bao giờ thu đủ 7 giá trị. Nếu muốn chọn 6 số duy nhất từ 1–6, hãy lấy một tập hữu hạn rồi xáo trộn bằng phương pháp phù hợp, hoặc dùng cách chọn không hoàn lại; sinh từng số ngẫu nhiên không tự bảo đảm tính duy nhất.

## 12. Xử lý parallel stream thận trọng

Có thể gọi `parallel()` trên stream, nhưng **không phải cứ bật parallel là nhanh hơn hoặc dễ tái lập hơn**:

~~~java
long count = new Random()
        .ints(100_000, 0, 1_000)
        .parallel()
        .filter(x -> x % 2 == 0)
        .count();
~~~

`Random` là thread-safe, nhưng nhiều luồng dùng chung bộ sinh có thể tranh chấp trạng thái và làm giảm hiệu năng. Thứ tự lấy số trong parallel stream có thể thay đổi theo lịch thực thi, nên **seed giống nhau không đủ để đảm bảo dãy phần tử/đầu ra giống hệt nhau khi xử lý song song**. Tránh test dựa trên đúng dãy số sau `parallel()` trừ khi bạn đã cố định, kiểm chứng và chấp nhận đầy đủ hợp đồng thứ tự của thuật toán/bộ sinh được chọn.

Trong bài toán hiệu năng cao trên nhiều luồng, xem xét `ThreadLocalRandom` hoặc bộ sinh thuộc nhóm `RandomGenerator.SplittableGenerator` như `SplittableRandom` với cách tách trạng thái phù hợp. Không dùng một `SplittableRandom` đơn lẻ đồng thời trên nhiều thread nếu không có thiết kế quản lý thread rõ ràng.

`limit()` với một stream có thứ tự gặp (*encounter order*) có thể tốn chi phí phối hợp lớn hơn ở parallel mode vì phải giữ đúng phần tử đầu tiên; với một nguồn không có thứ tự gặp, các bảo đảm và lựa chọn phần tử có thể khác. Do đó:
- Xác định xem kết quả cần thứ tự hay chỉ cần đặc tính tổng hợp.
- Benchmark trên dữ liệu thực tế trước khi chọn parallel.
- Giữ dữ liệu thao tác trong các lambda không can thiệp trạng thái nguồn và tránh side effect dùng chung.

Với các bài học hoặc batch số lượng vừa phải, stream **tuần tự** thường dễ giải thích và kiểm chứng nhất.

## 13. Lỗi tham số và tình huống biên

| Trường hợp | Kết quả / xử lý |
|---|---|
| `new Random().ints(0, 1, 7)` | Stream rỗng, không có mẫu |
| `new Random().ints(-1, 1, 7)` | `IllegalArgumentException`: kích thước âm |
| `new Random().ints(5, 7, 7)` | `IllegalArgumentException`: biên dưới không nhỏ hơn biên trên |
| `new Random().ints(1, 7).limit(-1)` | `IllegalArgumentException`: limit âm |
| `new Random().doubles(5, Double.NaN, 1.0)` | `IllegalArgumentException`: biên không hợp lệ |
| `new Random().ints(1, 7).limit(0)` | Stream rỗng |
| `new Random().ints(3, 1, 7).limit(5)` | Nhiều nhất 3 phần tử, không tự sinh thêm thành 5 |
| `new Random().ints(3, 1, 7).filter(x -> false)` | Không phần tử nào đi qua filter |

Đặc biệt với `int` và `long`, phép trừ `bound - origin` có thể bị tràn trong mã tự tính khoảng. Các overload `Random.ints(origin, bound)` và `longs(origin, bound)` được viết để xử lý trường hợp miền hợp lệ khó biểu diễn bằng phép trừ đơn giản. Hãy kiểm tra `origin < bound` và ưu tiên API thay vì xây công thức tự nhân chia bằng kiểu số nguyên có thể tràn.

Khi giới hạn số phần tử đầu ra cần **chính xác**, ghi rõ có phép `filter` hay `distinct` sau khi giới hạn. Nhiều phép biến đổi chỉ thay giá trị (ví dụ `map`) giữ số lượng; một số phép khác như `filter`/`distinct` có thể làm giảm số lượng.

## 14. Hai tình huống áp dụng hoàn chỉnh

### A. Sinh 12 lượt xúc xắc để tính tổng

~~~java
Random rng = new Random(2026L);
int total = rng.ints(12, 1, 7).sum();
System.out.println(total);
~~~

Đoạn đặt trong phương thức với `import java.util.Random;`. `sum()` tiêu thụ stream hữu hạn gồm 12 mặt xúc xắc (có thể trùng). Không phải lấy 12 mặt duy nhất. Với mẫu lớn hoặc giá trị có độ lớn lớn, cần cân nhắc tràn `int` khi dùng `sum()`; có thể chuyển sang `mapToLong` hoặc phép cộng kiểm tra tràn theo yêu cầu.

### B. Tạo tối đa 20 giá trị hợp lệ từ tối đa 1.000 lần thử

~~~java
Random rng = new Random();
int[] accepted = rng.ints(1_000, 0, 10_000)
        .filter(x -> x % 10 == 0)
        .limit(20)
        .toArray();
System.out.println("accepted = " + accepted.length);
~~~

Ví dụ này có hai giới hạn độc lập: không lấy quá 1.000 mẫu nguồn và không giữ quá 20 kết quả sau lọc. Nếu yêu cầu hệ thống là **bắt buộc có đủ 20 kết quả**, hãy xử lý trường hợp `accepted.length < 20` bằng chiến lược ứng dụng rõ ràng (ví dụ báo thiếu mẫu, tăng số lần thử có giới hạn, hoặc chuyển sang thuật toán khác). Không giả định xác suất sẽ luôn cung cấp đủ giá trị.

## 15. Tự kiểm tra

**Câu 1:** `new Random().ints(5, 10)` có bao nhiêu phần tử trước khi giới hạn?

**Đáp án:** Không có giới hạn kích thước truyền vào; đây là overload nhận `origin = 5`, `bound = 10`. Nguồn phát các số 5–9 và được xem là không giới hạn sẵn. Muốn tạo 5 số trong khoảng này, viết `ints(5, 5, 10)` hoặc `ints(5, 10).limit(5)`.

**Câu 2:** `random.ints(10, 1, 7).filter(x -> x == 6).count()` luôn trả 10?

**Đáp án:** Không. 10 là số lần lấy mẫu ban đầu; sau lọc chỉ những lần xuất hiện số 6 được đếm. Kết quả nằm trong `[0,10]`.

**Câu 3:** `random.ints(1, 7).filter(x -> x == 7).limit(1).findFirst()` có đảm bảo kết thúc?

**Đáp án:** Không. Khoảng `[1,7)` không chứa 7; bộ lọc không bao giờ chấp nhận một giá trị nào. Cần kiểm soát phạm vi và tối đa số lần thử.

**Câu 4:** Có nên dùng `random.ints().sorted().limit(10).toArray()` để lấy 10 số nhỏ nhất?

**Đáp án:** Không. `sorted()` cần tiêu thụ toàn bộ đầu vào để sắp xếp nên một nguồn không giới hạn sẵn không thể xử lý theo cách đó. Nếu muốn sắp 10 mẫu, dùng `ints().limit(10).sorted().toArray()`.

**Câu 5:** Hai đối tượng `new Random(42)` và `new Random(42)` chạy tuần tự, cùng pipeline `ints(5,1,7).toArray()` có thể tạo cùng kết quả không?

**Đáp án:** Có, với `java.util.Random` cùng seed và chuỗi lời gọi theo hợp đồng. Không mở rộng kết luận thành "mọi thuật toán bộ sinh và mọi parallel stream đều tạo cùng kết quả chỉ vì seed giống nhau".

**Câu 6:** Nếu yêu cầu "không có hơn 100 lượt thử nhưng cần tối đa 5 số chẵn", nên đặt `limit` ở đâu?

**Đáp án:** Dùng nguồn hữu hạn `ints(100, origin, bound)`, tiếp theo `filter(even).limit(5)`. Kết quả có thể ít hơn 5 nếu 100 lượt thử chưa tạo đủ số chẵn; giới hạn số lần thử vẫn được giữ.

## 16. Ghi nhớ và tài liệu chính thức

- `ints(streamSize, origin, bound)` là lựa chọn trực tiếp cho **kích thước và miền giá trị đều biết trước**.
- `ints(origin, bound).limit(n)` hợp lý khi nguồn không giới hạn sẵn cần chặn lại ở pipeline; luôn có terminal operation khi thực sự muốn xử lý.
- `origin` được **bao gồm**, `bound` bị **loại trừ**; `streamSize` và `limit` không âm. `limit(n)` đặt **trần n phần tử ở đúng vị trí** của nó, chứ không bảo đảm đủ n phần tử sau các bước lọc.
- Luồng xử lý lazy; `count()` có thể được tối ưu khi nguồn đã có kích thước biết trước. Đừng dựa vào side effect trong pipeline để suy ra chắc chắn mọi giá trị đã được sinh.
- Giới hạn cả số lần thử, số kết quả và tài nguyên; dùng bộ sinh thích hợp với mục đích, đặc biệt nếu có xử lý song song hoặc yêu cầu mật mã.

Tài liệu tham khảo:
- [Random — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html): overload `ints`, `longs`, `doubles`, validation và khả năng tái lập bằng seed.
- [IntStream — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/IntStream.html): terminal và intermediate operation trên số `int`.
- [Stream — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html): lazy evaluation, `filter`, `limit`, `sorted`, `count` và xử lý song song.
- [RandomGenerator — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/RandomGenerator.html): giao diện bộ sinh có dòng giá trị ngẫu nhiên kể từ Java 17.


</details>

- [Quay lại đầu trang](#back-to-top)
