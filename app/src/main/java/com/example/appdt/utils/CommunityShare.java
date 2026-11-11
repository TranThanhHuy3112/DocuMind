package com.example.appdt.utils;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;

import com.example.appdt.model.PostAttachment;
import com.example.appdt.model.PostModel;

public final class CommunityShare {
    private CommunityShare() { }

    public static Intent createSendIntent(Context context, PostModel post) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, post.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, post.getTitle() + "\n\n" + post.getContent());
        PostAttachment attachment = post.getAttachment();
        if (attachment == null || !"content".equalsIgnoreCase(attachment.getUri().getScheme())) {
            intent.setType("text/plain");
        } else {
            intent.setType(attachment.getMimeType());
            intent.putExtra(Intent.EXTRA_STREAM, attachment.getUri());
            intent.setClipData(ClipData.newUri(context.getContentResolver(), attachment.getName(),
                    attachment.getUri()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return intent;
    }
}
