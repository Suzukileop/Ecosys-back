package com.plateforme.user.entity;

import com.plateforme.marketplace.entity.ContentPost;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "creator_portfolio_posts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class CreatorPortfolioPost {

    @EmbeddedId
    private CreatorPortfolioPostId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("creatorUserId")
    @JoinColumn(name = "creator_user_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("contentPostId")
    @JoinColumn(name = "content_post_id", nullable = false)
    private ContentPost contentPost;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @CreatedDate
    @Column(name = "added_at", updatable = false)
    private LocalDateTime addedAt;

    public CreatorPortfolioPost(User creator, ContentPost contentPost, int sortOrder) {
        this.creator = creator;
        this.contentPost = contentPost;
        this.sortOrder = sortOrder;
        this.id = new CreatorPortfolioPostId(creator.getId(), contentPost.getId());
    }
}
