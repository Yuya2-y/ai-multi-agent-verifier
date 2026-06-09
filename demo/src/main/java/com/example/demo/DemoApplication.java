package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.annotation.Autowired;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Random;

@SpringBootApplication
@Controller
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Autowired
    private FortuneRepository fortuneRepository; // 💡 DBを操作する道具を自動で準備してもらう

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/omikuji")
    public String omikuji(Model model) {
        // 💡 データベースからすべての運勢データを取得する (裏側で SELECT * FROM fortune が自動実行されます)
        List<Fortune> fortunes = fortuneRepository.findAll();
        
        // ランダムに1つ選ぶ
        int index = new Random().nextInt(fortunes.size());
        String luckyResult = fortunes.get(index).getResultText();

        model.addAttribute("result", luckyResult);
        return "result";
    }
}

// 💡 データベースの「fortune」テーブルと連動するJavaのクラス（エンティティ）
@Entity
class Fortune {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String resultText;

    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
}

// 💡 SQLを自分で書かなくても、基本的なSQL（SELECTなど）を自動生成してくれるSpringの超便利仕組み（リポジトリ）
interface FortuneRepository extends JpaRepository<Fortune, Integer> {
}