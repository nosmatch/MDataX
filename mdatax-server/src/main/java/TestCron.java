import org.springframework.scheduling.support.CronExpression;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TestCron {
    public static void main(String[] args) {
        String[] crons = {
            "0 0 14 * * *",
            "0 0 14 * * ?",
            "0 0 2 * * *",
            "0 0 2 * * ?"
        };
        for (String cron : crons) {
            try {
                CronExpression expr = CronExpression.parse(cron);
                // 用 ZonedDateTime 替代 Instant
                ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
                ZonedDateTime next = expr.next(now);
                System.out.println(cron + " -> OK, next=" + next);
            } catch (Exception e) {
                System.out.println(cron + " -> FAILED: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
