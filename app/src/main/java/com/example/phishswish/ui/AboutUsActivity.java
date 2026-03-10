package com.example.phishswish.ui;

import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.phishswish.R;

public class AboutUsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_us);

        View root = findViewById(R.id.main);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
                return insets;
            });
        }

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        enableAvatarPreview(findViewById(R.id.ivMemberJohn));
        enableAvatarPreview(findViewById(R.id.ivMemberRome));
        enableAvatarPreview(findViewById(R.id.ivMemberVen));
        enableAvatarPreview(findViewById(R.id.ivMemberPrince));

        // Social Click Listeners
        setupSocialButtons();
    }

    private void setupSocialButtons() {
        // John Aldrich
        findViewById(R.id.ivFBJohn).setOnClickListener(v -> openUrl("https://www.facebook.com/share/14XmmmjpTSN/"));
        findViewById(R.id.ivIGJohn).setOnClickListener(v -> openUrl("https://www.instagram.com/richwalddd?igsh=bjIzZHo2cmJucGt3"));

        // Rome
        findViewById(R.id.ivFBRome).setOnClickListener(v -> openUrl("https://www.facebook.com/share/16Yi1zqpFc/"));
        findViewById(R.id.ivIGRome).setOnClickListener(v -> openUrl("https://www.instagram.com/romeantig?igsh=djd5bHlqbXRwdjc1"));

        // Ven
        findViewById(R.id.ivFBVen).setOnClickListener(v -> openUrl("https://www.facebook.com/share/1PvDxbEiVS/"));
        findViewById(R.id.ivIGVen).setOnClickListener(v -> openUrl("https://www.instagram.com/4nd.r_?igsh=dGpvNW9ramkyMGE5"));

        // Prince
        findViewById(R.id.ivFBPrince).setOnClickListener(v -> openUrl("https://www.facebook.com/share/16hooLfBvX/"));
        findViewById(R.id.ivIGPrince).setOnClickListener(v -> openUrl("https://www.instagram.com/lixvmo.ve?igsh=Zmh3azV5aDdoYnRy"));
    }

    private void openUrl(String url) {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Cannot open link", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}

