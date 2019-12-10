package redditorscannerbot;

import redditjackal.entities.Comment;

public class CommentWrapper {
    private Comment comment;
    private Comment parentComment;

    public CommentWrapper()  {}

    public CommentWrapper(Comment comment, Comment parentComment)  {
        this.comment = comment;
        this.parentComment = parentComment;
    }

    public void setComment(Comment comment) {
        this.comment = comment;
    }

    public void setParentComment(Comment parentComment) {
        this.parentComment = parentComment;
    }

    public Comment getComment() {
        return comment;
    }

    public Comment getParentComment() {
        return parentComment;
    }
}
