package com.example.annotationprocessor;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import com.jdqm.annotation.BindView;
import com.jdqm.lib_core.Butterknife;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.text_view1)
    TextView textView1;

    @BindView(R.id.text_view2)
    TextView textView2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        textView1 = findViewById(R.id.text_view1);
//        textView2 = findViewById(R.id.text_view2);
        Butterknife.bind(this);

        textView1.setText("Hello Annotation1");
        textView2.setText("Hello Annotation2");
    }
}