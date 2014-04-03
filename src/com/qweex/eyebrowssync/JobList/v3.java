package com.qweex.eyebrowssync.JobList;


import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.qweex.eyebrowssync.R;

import java.util.ArrayList;

public class v3 extends Base implements ListView.OnItemClickListener {
    ListView windowListview;
    PopupWindow window;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        window = new PopupWindow(this);
        window.setWidth(200);
        window.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        window.setOutsideTouchable(true);
        window.setBackgroundDrawable(new BitmapDrawable());
        windowListview = new ListView(this);
        window.setContentView(windowListview);
    }

    public boolean onKeyDown (int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && window.isShowing()) {
            window.dismiss();
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    // Overridden Methods
    @Override
    void showItemMenu(View v, String name, ArrayList<SimpleMenuItem> menuItems) {
        findById(menuItems, R.id.status).setVisible(syncers.get(name)!=null);
        filter(menuItems);
        windowListview.setAdapter(new MenuItemAdapter(v3.this, android.R.layout.simple_list_item_1, menuItems));
        window.showAsDropDown(v);
    }

    // Specific Methods
    public SimpleMenuItem findById(ArrayList<SimpleMenuItem> items, int id) {
        SimpleMenuItem smi = null;
        for(int i=0; i<items.size(); i++)
            if(items.get(i).getItemId()==id)
                smi = items.get(i);
        return smi;
    }

    public void filter(ArrayList<SimpleMenuItem> items) {
        SimpleMenuItem smi = null;
        for(int i=0; i<items.size(); i++)
            if(!items.get(i).getVisible())
                items.remove(i--);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        handleMenuItemClick(view.getId());
    }


    class MenuItemAdapter extends ArrayAdapter<SimpleMenuItem> {
        Drawable bg;

        int resId;

        public MenuItemAdapter(Context context, int resource) {
            super(context, resource);
            resId = resource;
        }

        public MenuItemAdapter(Context context, int resource, SimpleMenuItem[] objects) {
            super(context, resource, objects);
            resId = resource;
        }

        public MenuItemAdapter(Context context, int resource, ArrayList<SimpleMenuItem> objects) {
            super(context, resource, objects);
            resId = resource;
        }

        @Override
        public View getView(int position, View v, ViewGroup vg)
        {
            if(v==null) {
                v = ((LayoutInflater) getContext().getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(resId, vg, false);
                v.setBackgroundDrawable(getResources().getDrawable(R.drawable.v3_menu_selector));
                v.setOnClickListener(clickMenuItem);
            }

            TextView txt = (TextView) v.findViewById(android.R.id.text1);
            txt.setText(getItem(position).getTitle());
            v.setTag(getItem(position).getItemId());

            return v;
        }

        View.OnClickListener clickMenuItem = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleMenuItemClick((Integer) v.getTag());
                window.dismiss();
            }
        };
    }
}
