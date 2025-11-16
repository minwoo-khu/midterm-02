from django.urls import path
from rest_framework import routers
from . import views
from django.urls import include

router = routers.DefaultRouter()
router.register('Post', views.blogImage)

urlpatterns = [
    path('', views.post_list, name='post_list'),
    path('api_root/', include(router.urls)),
]