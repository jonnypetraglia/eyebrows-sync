package com.qweex.eyebrowssync.JobList;

import android.view.*;
import android.widget.*;
import com.qweex.eyebrowssync.R;

import java.util.ArrayList;

public class v11 extends Base implements PopupMenu.OnMenuItemClickListener {

    // Overridden Methods
    void showItemMenu(View v, String name, ArrayList<SimpleMenuItem> menuItems) {
        PopupMenu popupMenu = new PopupMenu(v11.this, v);
        popupMenu.getMenu().clear();

        for(int i=0; i<menuItems.size(); i++)
            popupMenu.getMenu().add(
                    0, menuItems.get(i).getItemId(),
                    0, menuItems.get(i).getTitle()
                    );

        popupMenu.getMenu().findItem(R.id.status).setVisible(syncers.get(name)!=null);

        popupMenu.setOnMenuItemClickListener(v11.this);
        popupMenu.show();
    }

    // Specific Methods
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        handleMenuItemClick(item.getItemId());
        return false;
    }
}
